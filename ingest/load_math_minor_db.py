"""Insert the versioned Math minor reference snapshot; no DDL, UPDATE or DELETE. Run --dry-run first."""
import argparse,json,pathlib
from load_catalog_db import settings
from load_cmpsc_db import schema_fingerprint

def load(cur,doc):
    before=schema_fingerprint(cur)
    code=doc['program']['code'];year=doc['catalog_year'];url=doc['source_url']
    cur.execute("INSERT INTO programs(code,name,program_type,college) VALUES (%s,%s,'minor',%s) ON CONFLICT DO NOTHING",(code,doc['program']['name'],doc['program']['college']))
    cur.execute('SELECT name,program_type,college FROM programs WHERE code=%s',(code,))
    assert cur.fetchone()==(doc['program']['name'],'minor',doc['program']['college'])
    cur.execute('INSERT INTO program_versions(program_code,catalog_year,min_total_credits,source_url) VALUES (%s,%s,%s,%s) ON CONFLICT DO NOTHING',(code,year,doc['credits_range'][0],url))
    cur.execute('SELECT min_total_credits,source_url FROM program_versions WHERE program_code=%s AND catalog_year=%s',(code,year));assert cur.fetchone()==(doc['credits_range'][0],url)
    cur.execute("SELECT value FROM policy_rules WHERE key='minor_audit_rules_v1' AND catalog_year=%s AND scope='program' AND scope_code=%s",(year,code));old=cur.fetchone()
    if old:assert old[0]==doc,'Existing snapshot differs; refusing replacement'
    else:cur.execute("INSERT INTO policy_rules(key,catalog_year,scope,scope_code,value,description,source_url) VALUES ('minor_audit_rules_v1',%s,'program',%s,%s::jsonb,%s,%s)",(year,code,json.dumps(doc),'Math minor reference rules; grades, distinct major credits, catalog-year applicability and approval require review.',url))
    assert schema_fingerprint(cur)==before
    cur.execute("SELECT value FROM policy_rules WHERE key='minor_audit_rules_v1' AND catalog_year=%s AND scope='program' AND scope_code=%s",(year,code));assert cur.fetchone()[0]==doc
    return {'program':code,'year':year,'new_snapshot':not bool(old),'schema_unchanged':True}
if __name__=='__main__':
    import psycopg
    ap=argparse.ArgumentParser();ap.add_argument('--dry-run',action='store_true');args=ap.parse_args()
    doc=json.loads((pathlib.Path(__file__).resolve().parents[1]/'data/catalog/2026-27/programs/MATH_UMNR.json').read_text())
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur:print(load(cur,doc))
        if args.dry_run:conn.rollback()
        else:conn.commit()
