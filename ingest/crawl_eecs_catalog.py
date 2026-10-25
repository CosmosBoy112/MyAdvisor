"""Crawl the 2026-27 Bulletin for the remaining University Park EECS majors (Computer Engineering, Electrical
Engineering) into the same JSON shape as programs/CMPSC_*.json, and merge their courses into courses.json.

Requires lxml. Only bulletins.psu.edu pages allowed by its robots.txt are fetched (program pages and
/university-course-descriptions/); /archive/ and /pdf/ are never requested. Downloads are cached outside the repo.
Run: python ingest/crawl_eecs_catalog.py --cache /tmp/eecs-sources --out data/catalog/2026-27 [--offline]

Every requirement row must be recognized; an unknown row, a changed edition or a missing course stops the crawl
instead of being guessed. Approved lists the Bulletin only names (EE 300/400-level lists) stay empty POOLs with
requires_review, never "any course".
"""
from __future__ import annotations
import argparse, copy, hashlib, json, re, ssl, time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import Request, urlopen
from lxml import html
from parse_bulletin_courses import parse_block, build_course

YEAR = '2026-27'
BASE = 'https://bulletins.psu.edu/undergraduate/colleges/engineering/'
PROGRAMS = {
    'CMPEN_BS': {'slug': 'computer-engineering-bs', 'name': 'Computer Engineering, B.S.', 'etm': 'CMPEN_ETM',
                 'etm_name': 'Computer Engineering Entrance to Major (University Park)'},
    'EE_BS': {'slug': 'electrical-engineering-bs', 'name': 'Electrical Engineering, B.S.', 'etm': 'EE_ETM',
              'etm_name': 'Electrical Engineering Entrance to Major (University Park)'},
}
GROUPS = {'Prescribed Courses': 'prescribed', 'Additional Courses': 'additional',
          'Supporting Courses and Related Areas': 'supporting_and_related'}
CODE = r'[A-Z]{2,6} \d{1,3}[A-Z]{0,2}'


def clean(value): return ' '.join(value.replace('\xa0', ' ').split())
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n')


def expanded_codes(label):
    m = re.fullmatch(r'([A-Z]+(?:/[A-Z]+)*) (\d+[A-Z]*)', clean(label))
    if not m: raise ValueError(f'Unrecognized course identifier: {label}')
    return [s + ' ' + m[2] for s in m[1].split('/')]


def credit_range(value):
    m = re.fullmatch(r'(\d+(?:\.\d+)?)(?:-(\d+(?:\.\d+)?))?', value)
    if not m: raise ValueError(f'Unrecognized credits: {value}')
    return float(m[1]), float(m[2] or m[1])


class Fetcher:
    def __init__(self, cache, offline=False):
        self.cache = cache; self.offline = offline; self.sources = []
        cache.mkdir(parents=True, exist_ok=True)
        self.context = ssl.create_default_context(cafile='/etc/ssl/cert.pem' if Path('/etc/ssl/cert.pem').exists() else None)

    def get(self, name, url, source_id):
        parsed = urlparse(url)
        if parsed.hostname != 'bulletins.psu.edu': raise ValueError('Only bulletins.psu.edu is allowed')
        if parsed.path.startswith(('/archive/', '/pdf/', '/search/')): raise ValueError('Path disallowed by robots.txt: ' + parsed.path)
        dest = self.cache / name
        if not dest.exists():
            if self.offline: raise FileNotFoundError(dest)
            with urlopen(Request(url, headers={'User-Agent': 'BLS-MyAdvisor-Catalog/1.0 (academic source indexing)'}), context=self.context, timeout=40) as r:
                if urlparse(r.url).hostname != 'bulletins.psu.edu': raise ValueError('Unexpected source redirect')
                dest.write_bytes(r.read())
            time.sleep(1)
        data = dest.read_bytes()
        self.sources.append({'id': source_id, 'url': url, 'cache_file': name, 'sha256': hashlib.sha256(data).hexdigest(),
                             'downloaded_at': datetime.fromtimestamp(dest.stat().st_mtime, timezone.utc).isoformat()})
        return data


def strip_sup(node):
    # Footnote markers are <sup> digits; they would otherwise merge into course numbers and credits.
    for s in node.xpath('.//sup'):
        s.drop_tree()  # keeps the text that follows the marker
    return node


def parse_program(code, raw):
    cfg = PROGRAMS[code]; url = BASE + cfg['slug'] + '/'
    doc = html.fromstring(raw)
    if '2026-2027 edition' not in clean(doc.text_content()): raise ValueError('Bulletin edition changed; do not overwrite 2026-27')
    req = doc.get_element_by_id('programrequirementstextcontainer')
    text = clean(req.text_content())
    total = int(re.search(r'a minimum of (\d+) credits is required', text)[1])
    major = credit_range(re.search(r'Requirements for the Major (\d+(?:-\d+)?)', text)[1])
    overlap = int(re.search(r'(\d+) of the 45 credits for General Education are included', text)[1])
    distinct = int(re.search(r'remaining (\d+) General Education credits must be distinct', text)[1])
    # "1 CMPSC 122 does not require a grade of C or better." -> leaves without a grade minimum.
    no_c = set(re.findall(r'\d (' + CODE + r') does not require a grade of C or better', text))

    def source(section, row=None):
        s = {'source_id': f'bulletin_{code.lower()}_2026_27', 'url': url + '#' + section + 'text', 'catalog_year': YEAR}
        if row is not None: s['table_row'] = row
        return s

    def leaf(label, grade, row):
        n = {'label': label, 'type': 'POOL', 'min_count': 1, 'rules': [{'course': c} for c in expanded_codes(label)], 'source': source('programrequirements', row)}
        if grade and not set(expanded_codes(label)) & no_c: n['min_grade'] = grade
        return n

    def course_node(labels, grade, row):
        children = [leaf(l, grade, row) for l in labels]
        if len(children) == 1: return children[0]
        return {'label': ' + '.join(labels), 'type': 'GROUP_ALL', 'children': children, 'source': source('programrequirements', row)}

    def close(sel):
        # A choice among single courses is one POOL; a choice that includes a course pair is GROUP_ANY.
        if sel is None or sel.get('_kind') != 'list': return
        kids = sel.pop('children'); sel.pop('_kind')
        if not kids: raise ValueError('Empty selection list: ' + sel['label'])
        if all(k['type'] == 'POOL' for k in kids):
            sel.update(type='POOL', rules=[r for k in kids for r in k['rules']])
        else:
            sel.update(type='GROUP_ANY', min_count=1, children=kids)

    tables = req.xpath('.//table[contains(@class,"sc_courselist")]')
    if len(tables) != 1: raise ValueError(f'{code}: expected one requirement table, found {len(tables)}')
    groups = []; group = None; grade = None; sel = None; last = None
    for rownum, row in enumerate(tables[0].xpath('.//tr')):
        if not row.xpath('./td'): continue
        strip_sup(row)
        words = clean(row.text_content()); classes = row.get('class', '').split()
        labels = [clean(a.get('title') or a.text_content()) for a in row.xpath('.//a[contains(@class,"code")]')]
        hours = row.xpath('./td[contains(@class,"hourscol")]'); credits = clean(hours[0].text_content()) if hours else ''
        if 'areaheader' in classes:
            close(sel); sel = None; last = None; grade = None
            title = clean(row.xpath('./td')[0].text_content())
            if title not in GROUPS: raise ValueError(f'{code}: unknown area header {title!r}')
            group = {'id': GROUPS[title], 'label': title, 'type': 'GROUP_ALL', 'children': [], 'source': source('programrequirements', rownum)}
            groups.append(group); continue
        if 'areasubheader' in classes:
            close(sel); sel = None; last = None
            if 'C or better' not in words: raise ValueError(f'{code}: unrecognized subheader {words!r}')
            grade = 'C'; continue
        if group is None: raise ValueError(f'{code}: row before the first area header')
        if labels:
            node = course_node(labels, grade, rownum)
            if 'orclass' in classes:
                target = sel['children'][-1] if sel is not None and sel.get('_kind') == 'list' and not last else last
                if target is None: raise ValueError(f'{code}: orphan OR row {rownum}')
                if target['type'] != 'GROUP_ANY':
                    old = copy.deepcopy(target)
                    target.clear(); target.update({'label': old['label'] + ' alternatives', 'type': 'GROUP_ANY', 'min_count': 1, 'children': [old], 'source': old['source']})
                    if 'credits_range' in old: target['credits_range'] = old.pop('credits_range')
                target['children'].append(node)
            elif sel is not None and sel.get('_kind') == 'list' and not credits:
                sel['children'].append(node)
            else:
                close(sel); sel = None
                if credits: node['credits_range'] = list(credit_range(credits))
                group['children'].append(node); last = node
            continue
        close(sel); sel = None; last = None
        if 'First-Year Seminar' in words:
            group['children'].append({'id': 'first_year_seminar', 'label': 'First-Year Seminar', 'type': 'POOL', 'min_credits': credit_range(credits)[0], 'rules': [{'attribute': 'FYS'}], 'source': source('programrequirements', rownum)})
        elif m := re.fullmatch(r'Select (\d+) credits (?:from|of) the following:?\s*\d*', words):
            sel = {'label': f'Select {m[1]} credits', 'min_credits': float(m[1]), '_kind': 'list', 'children': [], 'source': source('programrequirements', rownum)}
            if grade: sel['min_grade'] = grade
            group['children'].append(sel)
        elif m := re.fullmatch(r'Select (\d+) credits from any (\d)00-level ([A-Z]+(?: or [A-Z]+)*) courses?\s*\d*', words):
            level = int(m[2]) * 100
            n = {'label': f'{m[1]} credits of {m[2]}00-level {m[3]}', 'type': 'POOL', 'min_credits': float(m[1]),
                 'rules': [{'subject': s, 'level_min': level, 'level_max': level + 99} for s in m[3].split(' or ')], 'source': source('programrequirements', rownum)}
            if grade: n['min_grade'] = grade
            group['children'].append(n)
        elif m := re.fullmatch(r'Select (\d+) (?:additional )?credits from program-approved lists? of (.+?)\s*\d*', words):
            group['children'].append({'label': f'{m[1]} credits from the program-approved list of {m[2]}', 'type': 'POOL', 'min_credits': float(m[1]), 'rules': [],
                                      'requires_review': True, 'review': ['The Bulletin names a program-approved list but does not enumerate it. Do not treat an empty list as any course; confirm with the department.'],
                                      'source': source('programrequirements', rownum)})
        elif m := re.fullmatch(r'Select (\d+) additional credits, (.+?)\s*\d*', words):
            group['children'].append({'label': f'{m[1]} additional credits', 'type': 'POOL', 'min_credits': float(m[1]), 'rules': [], 'requires_review': True,
                                      'review': ['Bulletin wording: ' + m[2] + '. Eligible courses are not enumerated; confirm with the department.'],
                                      'source': source('programrequirements', rownum)})
        else:
            raise ValueError(f'{code}: unrecognized requirement row {rownum}: {words}')
    close(sel)
    requirements = groups + [{'id': 'graduation_gpa', 'label': 'Graduation GPA', 'type': 'GPA_MIN', 'min_gpa': 2.0, 'source': source('programrequirements')}]
    gened_nodes = []
    for attr, cr in [('GQ', 6), ('GWS', 9), ('GA', 3), ('GHW', 3), ('GH', 3), ('GS', 3), ('GN', 3), ('INTER', 6)]:
        n = {'label': attr, 'type': 'POOL', 'min_credits': cr, 'exclusive': False, 'rules': [{'attribute': attr}], 'source': source('programrequirements')}
        if attr in ['GQ', 'GWS']: n['min_grade'] = 'C'
        if attr != 'INTER': n.update(requires_review=True, review=['Foundation and breadth slots exclude Inter-Domain courses; the attribute filter alone cannot enforce this.'])
        gened_nodes.append(n)
    gened_nodes.append({'label': 'Exploration', 'type': 'POOL', 'min_credits': 9, 'rules': [], 'requires_review': True, 'review': ['3 GN credits plus 6 exploration credits; respect permitted attributes, language threshold, and non-double-counting.'], 'source': source('programrequirements')})
    requirements.append({'id': 'general_education', 'label': 'General Education', 'type': 'GROUP_ALL', 'children': gened_nodes, 'source': source('programrequirements')})
    for attr in ['US', 'IL', 'WAC']:
        requirements.append({'id': attr.lower(), 'label': attr, 'type': 'POOL', 'min_credits': 3, 'exclusive': False, 'rules': [{'attribute': attr}], 'source': source('programrequirements')})
    program = {'program': {'code': code, 'name': cfg['name'], 'type': 'major', 'college': 'Engineering'}, 'campus': 'UP', 'catalog_year': YEAR,
               'min_total_credits': total, 'source_urls': [url], 'status': 'partial_requires_review', 'options': [],
               'notes': [f'Applies to University Park, entry year 2026-27. Other campuses are not interchangeable.',
                         f'{overlap} GenEd credits overlap major requirements; {distinct} GenEd credits must be distinct. Never sum all nodes as independent credits.',
                         'Credit totals do not prove graduation eligibility. Unresolved pools and extra constraints must block a fully satisfied audit.'],
               'credit_summary': {'general_education': 45, 'major_min': major[0], 'major_max': major[1], 'gened_major_overlap': overlap, 'distinct_gened': distinct},
               'requirements': requirements}
    return program, parse_entrance(code, doc, url, source), parse_plan(code, doc, url)


def parse_entrance(code, doc, url, source):
    cfg = PROGRAMS[code]
    etm = strip_sup(doc.get_element_by_id('howtogetintextcontainer'))
    text = clean(etm.text_content())
    m = re.search(r'(\d+)-(\d+) graded Penn State credits \(excludes transfer and AP credits\) completed with a grade of C or better: (.+?) earned a minimum cumulative grade-point average \(GPA\) of ([\d.]+)', text)
    if not m: raise ValueError(f'{code}: entrance-to-major wording changed')
    children = []
    for part in re.split(r'[;,]', m[3]):
        alts = [a.strip() for a in part.split(' or ')]
        for a in alts:
            if not re.fullmatch(CODE, a): raise ValueError(f'{code}: unrecognized entrance course {a!r}')
        children.append({'label': ' or '.join(alts), 'type': 'POOL', 'min_count': 1, 'min_grade': 'C', 'rules': [{'course': a} for a in alts], 'source': source('howtogetin')})
    controlled = 'administrative enrollment controls' in text.lower()
    college_required = 'enrolled in the College of Engineering or the Division of Undergraduate Studies' in text
    notes = ['The requirement tree cannot enforce graded-credit origin or current college; evaluate eligibility fields separately.']
    if not controlled and 'under enrollment control' in text:
        notes.append('Bulletin: if the major is under enrollment control, a higher minimum GPA is likely to be needed, and students must be enrolled in Engineering or DUS when confirming the major.')
    return {'program': {'code': cfg['etm'], 'name': cfg['etm_name'], 'type': 'entrance', 'college': 'Engineering', 'entrance_for': code}, 'campus': 'UP', 'catalog_year': YEAR,
            'source_urls': [url + '#howtogetintext'],
            'eligibility': {'college_any_of': ['Engineering', 'Division of Undergraduate Studies'] if college_required else None, 'administrative_enrollment_controls': controlled,
                            'graded_penn_state_credits_min': int(m[1]), 'graded_penn_state_credits_max': int(m[2]), 'excluded_credit_sources': ['transfer', 'AP']},
            'requirements': [{'label': 'Entrance courses', 'type': 'GROUP_ALL', 'children': children},
                             {'label': 'Entrance cumulative GPA', 'type': 'GPA_MIN', 'min_gpa': float(m[4]), 'source': source('howtogetin')}],
            'requires_review': True, 'notes': notes}


def parse_plan(code, doc, url):
    section = strip_sup(doc.get_element_by_id('suggestedacademicplantextcontainer'))
    tables = section.xpath('.//table[contains(@class,"sc_plangrid")]')
    if len(tables) != 1: raise ValueError(f'{code}: expected one suggested plan table, found {len(tables)}')

    def cr(t):
        t = clean(t)
        return float(t) if re.fullmatch(r'\d+(?:\.\d+)?', t) else None
    terms = []; year = 0; fall = spring = None
    for tr in tables[0].xpath('.//tr'):
        cells = [clean(td.text_content()) for td in tr.xpath('./th|./td')]
        cells += [''] * (4 - len(cells))
        joined = ' '.join(c for c in cells if c)
        if re.fullmatch(r'(First|Second|Third|Fourth|Fifth) Year', joined):
            year += 1; fall = {'year': year, 'season': 'FA', 'courses': []}; spring = {'year': year, 'season': 'SP', 'courses': []}
            terms += [fall, spring]; continue
        if year == 0 or cells[0] in ('Fall', 'Summer') or joined.startswith('Total Credits'): continue
        if not cells[0] and not cells[2]:
            fall['total_credits'], spring['total_credits'] = cr(cells[1]), cr(cells[3]); continue
        for label, credits, term in ((cells[0], cells[1], fall), (cells[2], cells[3], spring)):
            if label:
                term['courses'].append({'label': re.sub(r'\s*[*#†‡]+\s*$', '', label).strip(), 'credits': cr(credits), 'credits_text': clean(credits), 'c_required': '*' in label})
    total = re.search(r'Total Credits\s+([\d.]+)', clean(section.text_content()))
    if len(terms) != 8 or not total: raise ValueError(f'{code}: suggested plan layout changed ({len(terms)} terms)')
    return {'key': 'suggested_academic_plan.DEFAULT',
            'value': {'campus': 'UP', 'total_credits': float(total[1]), 'terms': terms, 'symbols': {'c_required': 'Course requires a grade of C or better'}},
            'description': f'Suggested academic plan for {PROGRAMS[code]["name"]} at University Park, in effect for 2026-27. A suggested sequence, not a requirement.',
            'source_url': url + '#suggestedacademicplantext'}


def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get('children', []))


def reference_row(code, review):
    subject, number = code.split()
    return {'code': code, 'subject': subject, 'number': number, 'level': int(re.match(r'\d+', number)[0]) // 100 * 100, 'title': None, 'credits_min': None,
            'credits_max': None, 'repeatable': False, 'description': None, 'attributes': [], 'crosslists': [], 'prereq_text': None, 'prereq': None,
            'status': 'reference_only', 'review': [review], 'source_url': None}


def collect_courses(docs, fetch, out):
    wanted = {r['course'] for doc in docs for n in walk(doc['requirements']) for r in n.get('rules', []) if 'course' in r}
    existing = json.loads((out / 'courses.json').read_text())
    if existing['catalog_year'] != YEAR: raise ValueError('Wrong course catalog year')
    bycode = {c['code']: c for c in existing['courses']}
    # Only codes without a full (non reference-only) 2026-27 row need their subject page.
    need = sorted(c for c in wanted if c not in bycode or bycode[c]['status'] in ('reference_only', 'not_in_course_list'))
    blocks = {}
    for subject in sorted({c.split()[0].lower() for c in need}):
        raw = fetch.get('c_' + subject + '.html', f'https://bulletins.psu.edu/university-course-descriptions/undergraduate/{subject}/', f'course_{subject}_2026_27').decode()
        if '2026-27 edition of the University Bulletins' not in clean(html.fromstring(raw).text_content()):
            raise ValueError(f'Course page edition changed: {subject}')
        for block in raw.split('<div class="courseblock">')[1:]:
            c, title, credits, paras = parse_block(block)
            blocks[c] = (title, credits, paras, subject)
    matches = []; added = []; upgraded = []
    for c in sorted(wanted):
        if c in need:
            if c not in blocks: raise ValueError(f'{c} is required by a program but missing from its 2026-27 course page')
            title, credits, paras, subject = blocks[c]
            course = build_course(c, title, credits, paras, subject)
            course['description'] = None  # keep factual catalog fields, as for CMPSC
            course['catalog_year'] = YEAR; course['provenance'] = {'source_id': f'course_{subject}_2026_27', 'collected_for': 'EECS'}
            (upgraded if c in bycode else added).append(c); bycode[c] = course
        matches.append({'code': c, 'match_status': 'exact_course_code', 'course_status': bycode[c]['status'], 'source_url': bycode[c]['source_url'], 'review': bycode[c]['review']})
    refs = set()
    def references(n):
        if n.get('course'): refs.add(n['course'])
        for child in n.get('children', []): references(child)
    for c in need:
        references(bycode[c].get('prereq') or {}); refs.update(bycode[c].get('crosslists', []))
    for c in sorted(refs - set(bycode)):
        if c in blocks:
            row = build_course(c, *blocks[c]); row.update(description=None, prereq=None, crosslists=[], status='reference_only', review=['Reference-only row; prerequisite expansion intentionally stops here.'])
        else:
            row = reference_row(c, 'Referenced course page not collected.')
        bycode[c] = row; added.append(c)
    order = [c['code'] for c in existing['courses']]
    existing['courses'] = [bycode[c] for c in order] + [bycode[c] for c in sorted(set(bycode) - set(order))]
    dump(out / 'courses.json', existing)
    return {'catalog_year': YEAR, 'matches': matches, 'added': sorted(added), 'upgraded_from_reference': sorted(upgraded),
            'notes': ['Course existence is distinct from elective eligibility. Prerequisites with review flags need manual confirmation.']}


def main():
    ap = argparse.ArgumentParser(); ap.add_argument('--cache', type=Path, required=True); ap.add_argument('--out', type=Path, required=True); ap.add_argument('--offline', action='store_true'); a = ap.parse_args()
    fetch = Fetcher(a.cache, a.offline)
    docs = []; summary = {}
    for code, cfg in PROGRAMS.items():
        program, entrance, plan = parse_program(code, fetch.get(cfg['slug'] + '.html', BASE + cfg['slug'] + '/', f'bulletin_{code.lower()}_2026_27'))
        docs += [program, entrance]
        dump(a.out / f'programs/{code}.json', program); dump(a.out / f'programs/{cfg["etm"]}.json', entrance)
        dump(a.out / f'policies/{code}.json', {'program_code': code, 'catalog_year': YEAR, 'scope': 'program', 'rules': [plan]})
        summary[code] = {'nodes': sum(1 for _ in walk(program['requirements'])), 'review_nodes': sum(1 for n in walk(program['requirements']) if n.get('requires_review'))}
    match = collect_courses(docs, fetch, a.out)
    dump(a.out / 'references/EECS_course_matches.json', match)
    dump(a.out / 'references/EECS_sources.json', {'program_codes': list(PROGRAMS), 'catalog_year': YEAR, 'sources': fetch.sources})
    print(json.dumps({'programs': summary, 'course_references': len(match['matches']), 'courses_added': len(match['added']), 'courses_upgraded': len(match['upgraded_from_reference'])}))


if __name__ == '__main__':
    main()
