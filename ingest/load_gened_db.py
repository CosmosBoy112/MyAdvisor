"""Insert the 2026-27 General Education course list and the GENED requirement tree into EXISTING tables.

Usage:
  python ingest/load_gened_db.py --data data/catalog/2026-27 [--dry-run]

Insert-only: no DDL, UPDATE or DELETE. Courses from gened_courses.json get a 2026-27 course version (with
attributes, prerequisite tree and cross-lists) when they have none. A course version that already exists is never
changed: only attributes it is missing are added, and a prerequisite tree only when it has none (rows loaded
earlier as reference-only). An existing version whose title or credits differ from the crawl stops the load.
Program GENED is inserted once; identical reruns are no-ops and a divergent existing tree fails closed. Every row
that existed before the load is fingerprinted and must be unchanged.
"""
import argparse, json, sys
from pathlib import Path
from load_catalog_db import settings, code_parts
from load_cmpsc_db import canonical, digest, schema_fingerprint, read_tree, insert_node
from load_eecs_db import insert_prereq, prereq_courses

CODE = 'GENED'
YEAR = '2026-27'


def source_text(node):
    metadata = {k: v for k, v in node.items() if k not in ('children', 'rules')}
    return 'GenEd catalog metadata (not a verbatim quote): ' + json.dumps(metadata, ensure_ascii=False, sort_keys=True)


def planned_node(node):
    if node['type'] not in ('GROUP_ALL', 'POOL'): raise ValueError('Unsupported node type ' + node['type'])
    if node['type'] == 'POOL' and not node.get('rules'): raise ValueError('A GenEd pool needs rules: ' + node['label'])
    result = {'label': node['label'], 'node_type': node['type'], 'exclusive': node.get('exclusive', True), 'source_text': source_text(node)}
    for key in ('min_count', 'min_credits', 'min_grade', 'min_gpa'): result[key] = node.get(key)
    result['rules'] = [{'include': r.get('include', True), 'course_code': r.get('course'), **{k: r.get(k) for k in ('subject', 'attribute', 'level_min', 'level_max')}} for r in node.get('rules', [])]
    result['children'] = [planned_node(n) for n in node.get('children', [])]
    return result


def bundle(data):
    doc = json.loads((data / 'programs' / f'{CODE}.json').read_text())
    if doc['program']['code'] != CODE or doc['program']['type'] != 'gened' or doc['catalog_year'] != YEAR:
        raise ValueError('Expected the 2026-27 GENED program')
    courses = json.loads((data / 'gened_courses.json').read_text())
    if courses['catalog_year'] != YEAR: raise ValueError('Wrong course catalog year')
    sources = json.loads((data / 'references' / 'GENED_sources.json').read_text())
    policies = [
        ('catalog_snapshot', {'automatic_audit_allowed': False, 'source_digest': digest(doc), 'document': doc},
         'Lossless GenEd catalog snapshot. Own-major exclusion, flexibility substitutions and campus first-year rules are not modeled; this is not an automatic audit.'),
        ('course_list_check', courses['course_list_check'],
         'Differences between attributes printed on course pages and the Bulletin GenEd course lists. Course-page attributes are what was loaded.'),
        ('sources', sources, 'Bulletin pages fetched for the GenEd course list, with sha256.'),
    ]
    return doc, policies, courses['courses']


def bounds(cur):
    cur.execute('SELECT coalesce(max(id),0) FROM course_versions'); maxv = cur.fetchone()[0]
    cur.execute('SELECT coalesce(max(id),0) FROM prereq_nodes'); maxp = cur.fetchone()[0]
    cur.execute('SELECT code FROM courses'); codes = [r[0] for r in cur.fetchall()]
    cur.execute('SELECT course_version_id, attribute FROM course_attributes'); attrs = set(cur.fetchall())
    cur.execute('SELECT course_code, crosslisted_code FROM course_crosslists'); pairs = set(cur.fetchall())
    return maxv, maxp, codes, attrs, pairs


def fingerprints(cur, limits):
    """Fingerprints of the rows inside the pre-load bounds and of every other program's rows."""
    maxv, maxp, codes, _, _ = limits
    queries = {
        'courses': ('SELECT * FROM courses WHERE code=ANY(%s)', (codes,)),
        'course_versions': ('SELECT * FROM course_versions WHERE id<=%s', (maxv,)),
        'prereq_nodes': ('SELECT * FROM prereq_nodes WHERE id<=%s', (maxp,)),
        'programs': ('SELECT * FROM programs WHERE code<>%s', (CODE,)),
        'program_versions': ('SELECT * FROM program_versions WHERE program_code<>%s', (CODE,)),
        'requirement_nodes': ('SELECT n.* FROM requirement_nodes n JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code<>%s', (CODE,)),
        'requirement_rules': ('SELECT r.* FROM requirement_rules r JOIN requirement_nodes n ON n.id=r.node_id JOIN program_versions p ON p.id=n.program_version_id WHERE p.program_code<>%s', (CODE,)),
        'policy_rules': ('SELECT * FROM policy_rules WHERE scope_code IS NULL OR scope_code<>%s', (CODE,)),
    }
    for t in ('catalog_years', 'grades', 'program_options', 'course_restrictions', 'terms', 'course_offerings', 'credit_sources', 'credit_equivalencies', 'academic_calendar'):
        queries[t] = (f'SELECT * FROM {t}', ())
    out = {}
    for name, (query, args) in queries.items():
        cur.execute(f"SELECT count(*),md5(coalesce(string_agg(to_jsonb(t)::text, '|' ORDER BY to_jsonb(t)::text),'')) FROM ({query}) t", args)
        out[name] = cur.fetchone()
    return out


def load_courses(cur, courses):
    stats = {'courses_inserted': 0, 'course_versions_inserted': 0, 'existing_versions_matched': 0, 'attributes_inserted': 0,
             'attributes_added_to_existing': 0, 'prereq_trees_inserted': 0, 'reference_versions_completed': 0, 'crosslists_inserted': 0}
    codes = set()
    for c in courses:
        codes.add(c['code']); prereq_courses(c.get('prereq'), codes); codes.update(c.get('crosslists', []))
    for code in sorted(codes):
        cur.execute('INSERT INTO courses (code, subject, number, level) VALUES (%s,%s,%s,%s) ON CONFLICT (code) DO NOTHING', (code, *code_parts(code)))
        stats['courses_inserted'] += cur.rowcount
    conflicts, skipped = [], []
    for i, c in enumerate(courses, 1):
        if i % 250 == 0: print(f'{i}/{len(courses)} courses', file=sys.stderr, flush=True)
        if not c.get('title') or c.get('credits_min') is None:
            skipped.append(c['code']); continue
        cur.execute('SELECT id, title, credits_min, credits_max FROM course_versions WHERE course_code=%s AND catalog_year=%s', (c['code'], YEAR))
        row = cur.fetchone()
        if row:
            if (row[1], float(row[2]), float(row[3])) != (c['title'], float(c['credits_min']), float(c['credits_max'])):
                conflicts.append(f"{c['code']}: DB {row[1]!r} {row[2]}-{row[3]} vs Bulletin {c['title']!r} {c['credits_min']}-{c['credits_max']}"); continue
            vid = row[0]; stats['existing_versions_matched'] += 1
            for a in sorted(set(c['attributes'])):
                cur.execute('INSERT INTO course_attributes VALUES (%s,%s) ON CONFLICT DO NOTHING', (vid, a)); stats['attributes_added_to_existing'] += cur.rowcount
            if c.get('prereq'):
                cur.execute('SELECT count(*) FROM prereq_nodes WHERE course_version_id=%s', (vid,))
                if not cur.fetchone()[0]:
                    insert_prereq(cur, vid, c['prereq']); stats['reference_versions_completed'] += 1; stats['prereq_trees_inserted'] += 1
        else:
            cur.execute("""INSERT INTO course_versions (course_code, catalog_year, title, credits_min, credits_max, description, prereq_text, repeatable, source_url)
                           VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s) RETURNING id""",
                        (c['code'], YEAR, c['title'], c['credits_min'], c['credits_max'], c.get('description'), c.get('prereq_text'), c.get('repeatable', False), c.get('source_url')))
            vid = cur.fetchone()[0]; stats['course_versions_inserted'] += 1
            for a in sorted(set(c['attributes'])): cur.execute('INSERT INTO course_attributes VALUES (%s,%s)', (vid, a)); stats['attributes_inserted'] += 1
            if c.get('prereq'): insert_prereq(cur, vid, c['prereq']); stats['prereq_trees_inserted'] += 1
        for other in c.get('crosslists', []):
            a, b = sorted((c['code'], other))
            cur.execute('INSERT INTO course_crosslists VALUES (%s,%s) ON CONFLICT DO NOTHING', (a, b)); stats['crosslists_inserted'] += cur.rowcount
    if conflicts: raise ValueError('Existing course versions differ from the Bulletin crawl; review before loading:\n' + '\n'.join(conflicts))
    stats['skipped_without_title_or_credits'] = skipped
    return stats


def load(cur, data):
    doc, policies, courses = bundle(data)
    cur.execute('SELECT pg_advisory_xact_lock(726270131)')
    cur.execute("SET LOCAL lock_timeout = '10s'")
    cur.execute('LOCK TABLE courses,course_versions,course_attributes,course_crosslists,prereq_nodes,programs,program_versions,requirement_nodes,requirement_rules,policy_rules IN SHARE ROW EXCLUSIVE MODE')
    before_schema = schema_fingerprint(cur); limits = bounds(cur); before = fingerprints(cur, limits)
    cur.execute('SELECT code FROM catalog_years WHERE code=%s', (YEAR,))
    if not cur.fetchone(): raise ValueError('Required catalog year must already exist')
    print('loading courses', file=sys.stderr, flush=True)
    stats = load_courses(cur, courses)
    print('courses done; loading GENED program, policies and checking fingerprints', file=sys.stderr, flush=True)
    info = doc['program']; expected = (info['name'], 'gened', None, None)
    cur.execute('SELECT name,program_type,college,entrance_for FROM programs WHERE code=%s', (CODE,)); existing = cur.fetchone()
    if existing and existing != expected: raise ValueError('Existing program differs: ' + CODE)
    if not existing: cur.execute('INSERT INTO programs (code,name,program_type,college,entrance_for) VALUES (%s,%s,%s,%s,%s)', (CODE, *expected))
    tree = [planned_node(n) for n in doc['requirements']]
    cur.execute('SELECT id,min_total_credits,source_url FROM program_versions WHERE program_code=%s AND catalog_year=%s', (CODE, YEAR)); existing = cur.fetchone()
    if existing:
        pvid = existing[0]; stats['program_inserted'] = False
        if canonical(existing[1:]) != canonical([doc['min_total_credits'], doc['source_urls'][0]]) or read_tree(cur, pvid) != canonical(tree):
            raise ValueError('Existing GENED version/tree differs; refusing replacement')
    else:
        cur.execute('INSERT INTO program_versions (program_code,catalog_year,min_total_credits,source_url) VALUES (%s,%s,%s,%s) RETURNING id', (CODE, YEAR, doc['min_total_credits'], doc['source_urls'][0]))
        pvid = cur.fetchone()[0]; stats['program_inserted'] = True
        for i, node in enumerate(tree): insert_node(cur, pvid, node, order=i)
    if read_tree(cur, pvid) != canonical(tree): raise ValueError('Requirement tree round-trip failed')
    for key, value, description in policies:
        url = doc['source_urls'][0]
        cur.execute("SELECT value,description,source_url FROM policy_rules WHERE key=%s AND catalog_year=%s AND scope='program' AND scope_code=%s", (key, YEAR, CODE)); existing = cur.fetchone()
        if existing:
            if canonical(existing) != canonical((value, description, url)): raise ValueError('Existing policy differs: ' + key)
        else:
            cur.execute("INSERT INTO policy_rules (key,catalog_year,scope,scope_code,value,description,source_url) VALUES (%s,%s,'program',%s,%s::jsonb,%s,%s)", (key, YEAR, CODE, json.dumps(value), description, url))
    if before_schema != schema_fingerprint(cur): raise ValueError('Schema changed')
    after = fingerprints(cur, limits)
    changed = [t for t in before if before[t] != after[t]]
    cur.execute('SELECT course_version_id, attribute FROM course_attributes'); attrs_after = set(cur.fetchall())
    cur.execute('SELECT course_code, crosslisted_code FROM course_crosslists'); pairs_after = set(cur.fetchall())
    if not limits[3] <= attrs_after: changed.append('course_attributes')
    if not limits[4] <= pairs_after: changed.append('course_crosslists')
    if changed: raise ValueError('Rows that existed before the load, or other programs, changed: ' + ', '.join(changed))
    stats['schema_unchanged'] = True
    cur.execute("SELECT count(DISTINCT n.id),count(r.id) FROM program_versions p JOIN requirement_nodes n ON n.program_version_id=p.id LEFT JOIN requirement_rules r ON r.node_id=n.id WHERE p.program_code=%s AND p.catalog_year=%s", (CODE, YEAR))
    stats['gened_nodes'], stats['gened_rules'] = cur.fetchone()
    cur.execute('SELECT a.attribute,count(*) FROM course_attributes a JOIN course_versions v ON v.id=a.course_version_id WHERE v.catalog_year=%s GROUP BY 1 ORDER BY 1', (YEAR,))
    stats['attribute_counts'] = dict(cur.fetchall())
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
    print(json.dumps({'mode': 'rolled_back' if args.dry_run else 'committed', **stats}, ensure_ascii=False, indent=2, default=str))


if __name__ == '__main__':
    main()
