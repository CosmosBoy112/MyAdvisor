"""Insert UP CMPSC catalog into EXISTING tables, without DDL, updates or deletes.

Run --dry-run first. Identical reruns are no-ops; divergent existing CMPSC data
fails closed for explicit review. Shared courses and other programs are read-only.
"""
import argparse
import hashlib
import json
from decimal import Decimal
from pathlib import Path
from load_catalog_db import settings

CODES = ('CMPSC_BS', 'CMPSC_ETM')
YEAR = '2026-27'
NODE_FIELDS = ('label','node_type','min_count','min_credits','min_grade','min_gpa','exclusive','source_text')
RULE_FIELDS = ('include','course_code','subject','attribute','level_min','level_max')
SHARED = ('catalog_years','grades','courses','course_versions','course_attributes','course_crosslists','prereq_nodes','program_options','course_restrictions','terms','course_offerings','credit_sources','credit_equivalencies','academic_calendar')


def canonical(value):
    if isinstance(value, Decimal): return float(value)
    if isinstance(value, dict): return {k:canonical(v) for k,v in value.items()}
    if isinstance(value, (tuple,list)): return [canonical(v) for v in value]
    return value


def digest(value):
    return hashlib.sha256(json.dumps(canonical(value),sort_keys=True,ensure_ascii=False,default=str).encode()).hexdigest()


def source_text(node):
    metadata={k:v for k,v in node.items() if k not in ('children','rules')}
    return 'CMPSC catalog metadata (not a verbatim quote): '+json.dumps(metadata,ensure_ascii=False,sort_keys=True)


def planned_node(node):
    if node['type'] not in ('GROUP_ALL','GROUP_ANY','POOL','GPA_MIN'): raise ValueError('Unsupported node type')
    if node['type']=='POOL' and not node.get('rules') and not node.get('requires_review'):
        raise ValueError('An unresolved empty pool must require review')
    result={'label':node['label'],'node_type':node['type'], 'exclusive':node.get('exclusive',True), 'source_text':source_text(node)}
    for key in ('min_count','min_credits','min_grade','min_gpa'): result[key]=node.get(key)
    result['rules']=[{'include':r.get('include',True),'course_code':r.get('course'), **{k:r.get(k) for k in ('subject','attribute','level_min','level_max')}} for r in node.get('rules',[])]
    result['children']=[planned_node(n) for n in node.get('children',[])]
    return result


def bundle(data):
    docs=[json.loads((data/'programs'/f'{code}.json').read_text()) for code in CODES]
    for code,doc in zip(CODES,docs):
        if doc['program']['code']!=code or doc['campus']!='UP' or doc['catalog_year']!=YEAR or doc.get('options'):
            raise ValueError('Only option-free UP CMPSC 2026-27 is supported')
    refs={name:json.loads((data/'references'/f'CMPSC_{name}.json').read_text()) for name in ('handbook_crosswalk','course_matches','sources')}
    handbook=refs['handbook_crosswalk']
    if handbook['automatic_application_allowed'] or handbook['target_catalog_year']!=YEAR:
        raise ValueError('Historical handbook must remain reference-only')
    policies=[]
    for doc in docs:
        policies.append((doc['program']['code'],'catalog_snapshot',{'automatic_audit_allowed':False,'source_digest':digest(doc),'document':doc},'Lossless UP catalog snapshot. Evaluate review flags, overlap and eligibility separately; this is not an automatic audit.',doc['source_urls'][0]))
    for name in ('handbook_crosswalk','course_matches','sources'):
        policies.append(('CMPSC_BS',name,refs[name], 'Official source reference; historical handbook rules must not be applied to 2026-27 automatically.',handbook['handbook_url'] if name=='handbook_crosswalk' else docs[0]['source_urls'][0]))
    return docs,policies


def schema_fingerprint(cur):
    # Column types/defaults, constraints and indexes; never read or alter app data.
    cur.execute("""SELECT table_name,column_name,data_type,udt_name,is_nullable,column_default,is_identity,identity_generation
                   FROM information_schema.columns WHERE table_schema='public' ORDER BY table_name,ordinal_position""")
    columns=cur.fetchall()
    cur.execute("""SELECT c.relname,k.conname,pg_get_constraintdef(k.oid) FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid
                   JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' ORDER BY 1,2""")
    constraints=cur.fetchall()
    cur.execute("SELECT tablename,indexname,indexdef FROM pg_indexes WHERE schemaname='public' ORDER BY 1,2")
    return digest([columns,constraints,cur.fetchall()])


def protected_fingerprint(cur):
    result={}
    queries={t:f'SELECT * FROM {t}' for t in SHARED}
    queries.update({
      'programs':"SELECT * FROM programs WHERE code NOT IN ('CMPSC_BS','CMPSC_ETM')",
      'program_versions':"SELECT * FROM program_versions WHERE program_code NOT IN ('CMPSC_BS','CMPSC_ETM')",
      'requirement_nodes':"SELECT n.* FROM requirement_nodes n JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code NOT IN ('CMPSC_BS','CMPSC_ETM')",
      'requirement_rules':"SELECT r.* FROM requirement_rules r JOIN requirement_nodes n ON n.id=r.node_id JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code NOT IN ('CMPSC_BS','CMPSC_ETM')",
      'policy_rules':"SELECT * FROM policy_rules WHERE scope_code IS NULL OR scope_code NOT IN ('CMPSC_BS','CMPSC_ETM')"})
    for name,query in queries.items():
        cur.execute(f"SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text, '|' ORDER BY to_jsonb(t)::text),'')) FROM ({query}) t")
        result[name]=cur.fetchone()
    return result


def read_tree(cur,pvid):
    cur.execute('SELECT id,parent_id,sort_order,'+','.join(NODE_FIELDS)+' FROM requirement_nodes WHERE program_version_id=%s ORDER BY sort_order,id',(pvid,))
    rows=cur.fetchall();nodes={r[0]:dict(zip(NODE_FIELDS,r[3:]),rules=[],children=[]) for r in rows}
    cur.execute('SELECT r.node_id,'+','.join('r.'+k for k in RULE_FIELDS)+' FROM requirement_rules r JOIN requirement_nodes n ON n.id=r.node_id WHERE n.program_version_id=%s ORDER BY r.id',(pvid,))
    for r in cur.fetchall():nodes[r[0]]['rules'].append(dict(zip(RULE_FIELDS,r[1:])))
    roots=[]
    for r in rows:
        (roots if r[1] is None else nodes[r[1]]['children']).append(nodes[r[0]])
    return canonical(roots)


def insert_node(cur,pvid,node,parent=None,order=0):
    cur.execute('INSERT INTO requirement_nodes (program_version_id,parent_id,sort_order,'+','.join(NODE_FIELDS)+') VALUES ('+','.join(['%s']*(3+len(NODE_FIELDS)))+') RETURNING id',
                (pvid,parent,order,*(node[k] for k in NODE_FIELDS)))
    nid=cur.fetchone()[0]
    for rule in node['rules']:
        cur.execute('INSERT INTO requirement_rules (node_id,'+','.join(RULE_FIELDS)+') VALUES ('+','.join(['%s']*(1+len(RULE_FIELDS)))+')',(nid,*(rule[k] for k in RULE_FIELDS)))
    for i,child in enumerate(node['children']):insert_node(cur,pvid,child,nid,i)


def load(cur,data):
    docs,policies=bundle(data)
    cur.execute("SELECT pg_advisory_xact_lock(726270131)")
    cur.execute('SET LOCAL lock_timeout = \'10s\'')
    cur.execute('LOCK TABLE programs,program_versions,requirement_nodes,requirement_rules,policy_rules IN SHARE ROW EXCLUSIVE MODE')
    before_schema=schema_fingerprint(cur);before=protected_fingerprint(cur)
    cur.execute('SELECT code FROM catalog_years WHERE code=%s',(YEAR,))
    if not cur.fetchone():raise ValueError('Required catalog year must already exist')
    wanted={r['course'] for doc in docs for n in walk(doc['requirements']) for r in n.get('rules',[]) if 'course' in r}
    cur.execute('SELECT course_code FROM course_versions WHERE catalog_year=%s AND course_code=ANY(%s)',(YEAR,sorted(wanted)))
    missing=wanted-{r[0] for r in cur.fetchall()}
    if missing:raise ValueError('Required shared course versions missing: '+', '.join(sorted(missing)))
    # Shared courses were loaded by DATSC already. Leave versions, prerequisites and crosslists untouched.
    inserted=[]
    for doc in docs:
        info=doc['program'];code=info['code'];expected=(info['name'],info['type'],info.get('college'),info.get('entrance_for'))
        cur.execute('SELECT name,program_type,college,entrance_for FROM programs WHERE code=%s',(code,));existing=cur.fetchone()
        if existing and existing!=expected:raise ValueError('Existing program differs: '+code)
        if not existing:cur.execute('INSERT INTO programs (code,name,program_type,college,entrance_for) VALUES (%s,%s,%s,%s,%s)',(code,*expected))
        cur.execute('SELECT id,min_total_credits,source_url FROM program_versions WHERE program_code=%s AND catalog_year=%s',(code,YEAR));existing=cur.fetchone()
        expected_tree=[planned_node(n) for n in doc['requirements']]
        if existing:
            pvid=existing[0]
            if canonical(existing[1:])!=canonical([doc.get('min_total_credits'),doc['source_urls'][0]]) or read_tree(cur,pvid)!=canonical(expected_tree):
                raise ValueError('Existing program version/tree differs; refusing replacement: '+code)
        else:
            cur.execute('INSERT INTO program_versions (program_code,catalog_year,min_total_credits,source_url) VALUES (%s,%s,%s,%s) RETURNING id',(code,YEAR,doc.get('min_total_credits'),doc['source_urls'][0]))
            pvid=cur.fetchone()[0]
            for i,node in enumerate(expected_tree):insert_node(cur,pvid,node,order=i)
            inserted.append(code)
        if read_tree(cur,pvid)!=canonical(expected_tree):raise ValueError('Requirement tree round-trip failed: '+code)
    for code,key,value,description,url in policies:
        cur.execute("SELECT value,description,source_url FROM policy_rules WHERE key=%s AND catalog_year=%s AND scope='program' AND scope_code=%s",(key,YEAR,code));existing=cur.fetchone()
        if existing:
            if existing!=(value,description,url):raise ValueError('Existing policy differs: '+key)
        else:
            cur.execute("INSERT INTO policy_rules (key,catalog_year,scope,scope_code,value,description,source_url) VALUES (%s,%s,'program',%s,%s::jsonb,%s,%s)",(key,YEAR,code,json.dumps(value),description,url))
    if before_schema!=schema_fingerprint(cur):raise ValueError('Schema changed')
    if before!=protected_fingerprint(cur):raise ValueError('Shared or other-program catalog data changed')
    stats={'inserted_programs':inserted,'verified_course_references':len(wanted),'schema_unchanged':True,'shared_and_other_program_data_unchanged':True}
    cur.execute("SELECT p.program_code,count(DISTINCT n.id),count(r.id) FROM program_versions p JOIN requirement_nodes n ON n.program_version_id=p.id LEFT JOIN requirement_rules r ON r.node_id=n.id WHERE p.program_code=ANY(%s) AND p.catalog_year=%s GROUP BY p.program_code ORDER BY p.program_code",(list(CODES),YEAR));stats['program_counts']=[{'code':r[0],'nodes':r[1],'rules':r[2]} for r in cur.fetchall()]
    stats['policy_rules']=len(policies)
    stats['table_counts']={}
    for table in ('courses','course_versions','prereq_nodes','programs','program_versions','program_options','requirement_nodes','requirement_rules','policy_rules'):
        cur.execute('SELECT count(*) FROM '+table);stats['table_counts'][table]=cur.fetchone()[0]
    return stats


def walk(nodes):
    for node in nodes:
        yield node
        yield from walk(node.get('children',[]))


def main():
    ap=argparse.ArgumentParser(description=__doc__);ap.add_argument('--data',type=Path,required=True);ap.add_argument('--dry-run',action='store_true');args=ap.parse_args()
    import psycopg
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur:stats=load(cur,args.data)
        if args.dry_run:conn.rollback()
    print(json.dumps({'mode':'rolled_back' if args.dry_run else 'committed',**stats},ensure_ascii=False,indent=2))

if __name__=='__main__':main()
