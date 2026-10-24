"""Conservative label crosswalk, NOT an audit or course-eligibility decision."""
import argparse
import json
import re
from pathlib import Path

DEFAULT_CATALOG = Path(__file__).resolve().parents[1] / 'data/catalog/2026-27'


def normalize(label):
    return re.sub(r'[^a-z0-9]+', ' ', label.casefold()).strip()


def walk(nodes):
    for node in nodes:
        yield node
        yield from walk(node.get('children', []))


def match_requirement(label, catalog_year, campus, catalog=DEFAULT_CATALOG):
    program = json.loads((catalog / 'programs/CMPSC_BS.json').read_text())
    crosswalk = json.loads((catalog / 'references/CMPSC_handbook_crosswalk.json').read_text())
    result = {'input_label': label, 'catalog_year': catalog_year, 'campus': campus,
              'requires_review': True, 'automatic_application_allowed': False}
    if catalog_year != program['catalog_year'] or campus != program['campus']:
        return dict(result, status='unsupported_catalog_or_campus', requirement=None)
    candidates = [m for m in crosswalk['mappings']
                  if normalize(label) in {normalize(a) for a in m['report_aliases']}]
    if len(candidates) != 1:
        return dict(result, status='unmatched' if not candidates else 'ambiguous', requirement=None)
    mapping = candidates[0]
    node = next((n for n in walk(program['requirements'])
                 if n.get('id') == mapping['bulletin_requirement_id']), None) if mapping['bulletin_requirement_id'] else None
    return dict(result, status=mapping['match_status'], requirement=node,
                explanation=mapping['summary'], handbook_year=crosswalk['handbook_year'],
                handbook_url=crosswalk['handbook_url'], handbook_pages=mapping['handbook_pages'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('label')
    parser.add_argument('--catalog-year', required=True)
    parser.add_argument('--campus', required=True, help='UP for University Park')
    parser.add_argument('--catalog', type=Path, default=DEFAULT_CATALOG)
    args = parser.parse_args()
    print(json.dumps(match_requirement(args.label, args.catalog_year, args.campus, args.catalog), ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
