"""Insert the UP Computer Engineering and Electrical Engineering 2026-27 catalog into EXISTING tables.

Usage:
  python ingest/load_eecs_db.py --data data/catalog/2026-27 [--dry-run]

Insert-only: no DDL, UPDATE or DELETE. New course versions (with attributes, prerequisite trees and cross-lists)
are added; an existing course version is never changed, except that a version loaded earlier as a reference-only
row (no prerequisite tree) gets its tree inserted. An existing row whose title or credits differ from the crawl
stops the load. Programs CMPEN_BS/CMPEN_ETM/EE_BS/EE_ETM are inserted once; identical reruns are no-ops and a
divergent existing tree fails closed. Every row that existed before the load is fingerprinted and must be unchanged.
"""
import argparse, json
from pathlib import Path
from load_catalog_db import settings, code_parts
from load_cmpsc_db import canonical, digest, schema_fingerprint, read_tree, insert_node, walk

CODES = ('CMPEN_BS', 'CMPEN_ETM', 'EE_BS', 'EE_ETM')
MAJORS = ('CMPEN_BS', 'EE_BS')
YEAR = '2026-27'


def source_text(node):
    metadata = {k: v for k, v in node.items() if k not in ('children', 'rules')}
    return 'EECS catalog metadata (not a verbatim quote): ' + json.dumps(metadata, ensure_ascii=False, sort_keys=True)


def planned_node(node):
    if node['type'] not in ('GROUP_ALL', 'GROUP_ANY', 'POOL', 'GPA_MIN'): raise ValueError('Unsupported node type ' + node['type'])
    if node['type'] == 'POOL' and not node.get('rules') and not node.get('requires_review'):
        raise ValueError('An unresolved empty pool must require review: ' + node['label'])
    result = {'label': node['label'], 'node_type': node['type'], 'exclusive': node.get('exclusive', True), 'source_text': source_text(node)}
    for key in ('min_count', 'min_credits', 'min_grade', 'min_gpa'): result[key] = node.get(key)
    result['rules'] = [{'include': r.get('include', True), 'course_code': r.get('course'), **{k: r.get(k) for k in ('subject', 'attribute', 'level_min', 'level_max')}} for r in node.get('rules', [])]
    result['children'] = [planned_node(n) for n in node.get('children', [])]
    return result


def bundle(data):
    docs = [json.loads((data / 'programs' / f'{code}.json').read_text()) for code in CODES]
    for code, doc in zip(CODES, docs):
        if doc['program']['code'] != code or doc['campus'] != 'UP' or doc['catalog_year'] != YEAR or doc.get('options'):
            raise ValueError('Only option-free UP 2026-27 programs are supported: ' + code)
    refs = {name: json.loads((data / 'references' / f'EECS_{name}.json').read_text()) for name in ('course_matches', 'sources')}
    policies = []
    for doc in docs:
        policies.append((doc['program']['code'], 'catalog_snapshot', {'automatic_audit_allowed': False, 'source_digest': digest(doc), 'document': doc},
                         'Lossless UP catalog snapshot. Evaluate review flags, overlap and eligibility separately; this is not an automatic audit.', doc['source_urls'][0]))
    for code in MAJORS:
        for rule in json.loads((data / 'policies' / f'{code}.json').read_text())['rules']:
            policies.append((code, rule['key'], rule['value'], rule['description'], rule['source_url']))
        url = docs[CODES.index(code)]['source_urls'][0]
        for name, value in refs.items():
            policies.append((code, name, value, 'Official Bulletin source reference collected for the EECS majors (Computer Engineering, Electrical Engineering).', url))
    courses = json.loads((data / 'courses.json').read_text())
    if courses['catalog_year'] != YEAR: raise ValueError('Wrong course catalog year')
    bycode = {c['code']: c for c in courses['courses']}
    touched = sorted(set(refs['course_matches']['added']) | set(refs['course_matches']['upgraded_from_reference']))
    return docs, policies, [bycode[c] for c in touched], refs['course_matches']['upgraded_from_reference']


def bounds(cur):
    """What exists before the load: max ids, course codes and cross-list pairs. Rows past these bounds are new."""
    cur.execute('SELECT coalesce(max(id),0) FROM course_versions'); maxv = cur.fetchone()[0]
    cur.execute('SELECT coalesce(max(id),0) FROM prereq_nodes'); maxp = cur.fetchone()[0]
    cur.execute('SELECT code FROM courses'); codes = [r[0] for r in cur.fetchall()]
    cur.execute('SELECT course_code, crosslisted_code FROM course_crosslists'); pairs = sorted(cur.fetchall())
    return maxv, maxp, codes, pairs


def fingerprints(cur, limits):
    """Fingerprints of the rows inside the pre-load bounds and of every other program's rows."""
    maxv, maxp, codes, pairs = limits
    queries = {
        'courses': ('SELECT * FROM courses WHERE code=ANY(%s)', (codes,)),
        'course_versions': ('SELECT * FROM course_versions WHERE id<=%s', (maxv,)),
        'course_attributes': ('SELECT * FROM course_attributes WHERE course_version_id<=%s', (maxv,)),
        'prereq_nodes': ('SELECT * FROM prereq_nodes WHERE id<=%s', (maxp,)),
        'programs': ('SELECT * FROM programs WHERE code<>ALL(%s)', (list(CODES),)),
        'program_versions': ('SELECT * FROM program_versions WHERE program_code<>ALL(%s)', (list(CODES),)),
        'program_options': ('SELECT * FROM program_options', ()),
        'requirement_nodes': ('SELECT n.* FROM requirement_nodes n JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code<>ALL(%s)', (list(CODES),)),
        'requirement_rules': ('SELECT r.* FROM requirement_rules r JOIN requirement_nodes n ON n.id=r.node_id JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code<>ALL(%s)', (list(CODES),)),
        'policy_rules': ('SELECT * FROM policy_rules WHERE scope_code IS NULL OR scope_code<>ALL(%s)', (list(CODES),)),
    }
    for t in ('catalog_years', 'grades', 'course_restrictions', 'terms', 'course_offerings', 'credit_sources', 'credit_equivalencies', 'academic_calendar'):
        queries[t] = (f'SELECT * FROM {t}', ())
    out = {}
    for name, (query, args) in queries.items():
        cur.execute(f"SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text, '|' ORDER BY to_jsonb(t)::text),'')) FROM ({query}) t", args)
        out[name] = cur.fetchone()
    cur.execute('SELECT course_code, crosslisted_code FROM course_crosslists'); out['crosslists_kept'] = set(pairs) <= set(cur.fetchall())
    return out


def insert_prereq(cur, vid, node, parent=None, order=0):
    cur.execute("""INSERT INTO prereq_nodes (course_version_id, parent_id, sort_order, node_type, required_course, min_grade, min_semester, raw_text)
                   VALUES (%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                (vid, parent, order, node['type'], node.get('course'), node.get('min_grade'), node.get('min_semester'), node.get('raw')))
    nid = cur.fetchone()[0]
    for i, child in enumerate(node.get('children', [])): insert_prereq(cur, vid, child, nid, i)


def prereq_courses(node, acc):
    if node:
        if node.get('course'): acc.add(node['course'])
        for c in node.get('children', []): prereq_courses(c, acc)
    return acc


def load_courses(cur, courses, upgraded):
    stats = {'courses_inserted': 0, 'course_versions_inserted': 0, 'prereq_trees_inserted': 0, 'reference_versions_completed': 0, 'crosslists_inserted': 0}
    codes = set()
    for c in courses:
        codes.add(c['code']); prereq_courses(c.get('prereq'), codes); codes.update(c.get('crosslists', []))
    for code in sorted(codes):
        cur.execute('INSERT INTO courses (code, subject, number, level) VALUES (%s,%s,%s,%s) ON CONFLICT (code) DO NOTHING', (code, *code_parts(code)))
        stats['courses_inserted'] += cur.rowcount
    conflicts = []
    for c in courses:
        if not c.get('title') or c.get('credits_min') is None: continue  # code-only reference
        cur.execute('SELECT id, title, credits_min, credits_max FROM course_versions WHERE course_code=%s AND catalog_year=%s', (c['code'], YEAR))
        row = cur.fetchone()
        if row:
            if (row[1], float(row[2]), float(row[3])) != (c['title'], float(c['credits_min']), float(c['credits_max'])):
                conflicts.append(f"{c['code']}: DB {row[1]!r} {row[2]}-{row[3]} vs Bulletin {c['title']!r} {c['credits_min']}-{c['credits_max']}"); continue
            if c['code'] not in upgraded or not c.get('prereq'): continue
            cur.execute('SELECT count(*) FROM prereq_nodes WHERE course_version_id=%s', (row[0],))
            if cur.fetchone()[0]: continue  # already has a tree; leave it
            insert_prereq(cur, row[0], c['prereq']); stats['reference_versions_completed'] += 1; stats['prereq_trees_inserted'] += 1
            continue
        cur.execute("""INSERT INTO course_versions (course_code, catalog_year, title, credits_min, credits_max, description, prereq_text, repeatable, source_url)
                       VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                    (c['code'], YEAR, c['title'], c['credits_min'], c['credits_max'], c.get('description'), c.get('prereq_text'), c.get('repeatable', False), c.get('source_url')))
        vid = cur.fetchone()[0]; stats['course_versions_inserted'] += 1
        for a in sorted(set(c['attributes'])): cur.execute('INSERT INTO course_attributes VALUES (%s,%s)', (vid, a))
        if c.get('prereq'): insert_prereq(cur, vid, c['prereq']); stats['prereq_trees_inserted'] += 1
        for other in c.get('crosslists', []):
            a, b = sorted((c['code'], other))
            cur.execute('INSERT INTO course_crosslists VALUES (%s,%s) ON CONFLICT DO NOTHING', (a, b)); stats['crosslists_inserted'] += cur.rowcount
    if conflicts: raise ValueError('Existing course versions differ from the Bulletin crawl; review before loading:\n' + '\n'.join(conflicts))
    return stats


def load(cur, data):
    docs, policies, courses, upgraded = bundle(data)
    cur.execute('SELECT pg_advisory_xact_lock(726270131)')
    cur.execute("SET LOCAL lock_timeout = '10s'")
    cur.execute('LOCK TABLE courses,course_versions,course_attributes,course_crosslists,prereq_nodes,programs,program_versions,requirement_nodes,requirement_rules,policy_rules IN SHARE ROW EXCLUSIVE MODE')
    before_schema = schema_fingerprint(cur); limits = bounds(cur); before = fingerprints(cur, limits)
    cur.execute('SELECT code FROM catalog_years WHERE code=%s', (YEAR,))
    if not cur.fetchone(): raise ValueError('Required catalog year must already exist')
    stats = load_courses(cur, courses, set(upgraded))
    wanted = {r['course'] for doc in docs for n in walk(doc['requirements']) for r in n.get('rules', []) if 'course' in r}
    cur.execute('SELECT course_code FROM course_versions WHERE catalog_year=%s AND course_code=ANY(%s)', (YEAR, sorted(wanted)))
    missing = wanted - {r[0] for r in cur.fetchall()}
    if missing: raise ValueError('Program courses without a 2026-27 course version: ' + ', '.join(sorted(missing)))
    inserted = []
    for doc in sorted(docs, key=lambda d: d['program']['type'] == 'entrance'):  # majors first so entrance_for resolves
        info = doc['program']; code = info['code']; expected = (info['name'], info['type'], info.get('college'), info.get('entrance_for'))
        cur.execute('SELECT name,program_type,college,entrance_for FROM programs WHERE code=%s', (code,)); existing = cur.fetchone()
        if existing and existing != expected: raise ValueError('Existing program differs: ' + code)
        if not existing: cur.execute('INSERT INTO programs (code,name,program_type,college,entrance_for) VALUES (%s,%s,%s,%s,%s)', (code, *expected))
        cur.execute('SELECT id,min_total_credits,source_url FROM program_versions WHERE program_code=%s AND catalog_year=%s', (code, YEAR)); existing = cur.fetchone()
        tree = [planned_node(n) for n in doc['requirements']]
        if existing:
            pvid = existing[0]
            if canonical(existing[1:]) != canonical([doc.get('min_total_credits'), doc['source_urls'][0]]) or read_tree(cur, pvid) != canonical(tree):
                raise ValueError('Existing program version/tree differs; refusing replacement: ' + code)
        else:
            cur.execute('INSERT INTO program_versions (program_code,catalog_year,min_total_credits,source_url) VALUES (%s,%s,%s,%s) RETURNING id', (code, YEAR, doc.get('min_total_credits'), doc['source_urls'][0]))
            pvid = cur.fetchone()[0]
            for i, node in enumerate(tree): insert_node(cur, pvid, node, order=i)
            inserted.append(code)
        if read_tree(cur, pvid) != canonical(tree): raise ValueError('Requirement tree round-trip failed: ' + code)
    for code, key, value, description, url in policies:
        cur.execute("SELECT value,description,source_url FROM policy_rules WHERE key=%s AND catalog_year=%s AND scope='program' AND scope_code=%s", (key, YEAR, code)); existing = cur.fetchone()
        if existing:
            if canonical(existing) != canonical((value, description, url)): raise ValueError(f'Existing policy differs: {code} {key}')
        else:
            cur.execute("INSERT INTO policy_rules (key,catalog_year,scope,scope_code,value,description,source_url) VALUES (%s,%s,'program',%s,%s::jsonb,%s,%s)", (key, YEAR, code, json.dumps(value), description, url))
    if before_schema != schema_fingerprint(cur): raise ValueError('Schema changed')
    after = fingerprints(cur, limits)
    changed = [t for t in before if before[t] != after[t]]
    if changed: raise ValueError('Rows that existed before the load, or other programs, changed: ' + ', '.join(changed))
    stats.update({'inserted_programs': inserted, 'verified_course_references': len(wanted), 'policy_rules': len(policies), 'schema_unchanged': True})
    cur.execute("SELECT p.program_code,count(DISTINCT n.id),count(r.id) FROM program_versions p JOIN requirement_nodes n ON n.program_version_id=p.id LEFT JOIN requirement_rules r ON r.node_id=n.id WHERE p.program_code=ANY(%s) AND p.catalog_year=%s GROUP BY 1 ORDER BY 1", (list(CODES), YEAR))
    stats['program_counts'] = [{'code': r[0], 'nodes': r[1], 'rules': r[2]} for r in cur.fetchall()]
    stats['table_counts'] = {}
    for table in ('courses', 'course_versions', 'course_attributes', 'course_crosslists', 'prereq_nodes', 'programs', 'program_versions', 'requirement_nodes', 'requirement_rules', 'policy_rules'):
        cur.execute('SELECT count(*) FROM ' + table); stats['table_counts'][table] = cur.fetchone()[0]
    return stats


def main():
    ap = argparse.ArgumentParser(description=__doc__); ap.add_argument('--data', type=Path, required=True); ap.add_argument('--dry-run', action='store_true'); args = ap.parse_args()
    import psycopg
    with psycopg.connect(**settings()) as conn:
        with conn.cursor() as cur: stats = load(cur, args.data)
        if args.dry_run: conn.rollback()
    print(json.dumps({'mode': 'rolled_back' if args.dry_run else 'committed', **stats}, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
