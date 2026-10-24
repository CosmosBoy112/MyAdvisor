import json
import unittest
from pathlib import Path
from crawl_cmpsc_catalog import expanded_codes, course_node, parse_program, walk
from match_cmpsc_requirements import match_requirement, DEFAULT_CATALOG


def read(name):
    return json.loads((DEFAULT_CATALOG / name).read_text())


class CatalogTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.program = read('programs/CMPSC_BS.json')
        cls.nodes = list(walk(cls.program['requirements']))

    def test_scope_and_totals(self):
        self.assertEqual((self.program['campus'], self.program['catalog_year'], self.program['min_total_credits']), ('UP', '2026-27', 127))
        etm = read('programs/CMPSC_ETM.json')
        self.assertEqual(etm['requirements'][1]['min_gpa'], 3.2)
        self.assertEqual(etm['eligibility']['graded_penn_state_credits_min'], 29)
        self.assertEqual(etm['eligibility']['graded_penn_state_credits_max'], 55)

    def test_crosslists_are_alternatives(self):
        self.assertEqual(expanded_codes('STAT/MATH 414'), ['STAT 414', 'MATH 414'])
        self.assertEqual(course_node(['STAT/MATH 414'], None, 0)['min_count'], 1)

    def test_compound_alternatives_preserved(self):
        group = next(n for n in self.nodes if n.get('type') == 'GROUP_ANY' and n['label'].startswith('CMPEN 270'))
        self.assertEqual(group['min_count'], 1)
        combined = group['children'][1]
        self.assertEqual(combined['type'], 'GROUP_ALL')
        self.assertEqual([n['rules'][0]['course'] for n in combined['children']], ['CMPEN 271', 'CMPEN 275'])
        self.assertTrue(all(n['min_grade'] == 'C' for n in combined['children']))

    def test_all_course_references_resolve(self):
        courses = read('courses.json')['courses']
        codes = [c['code'] for c in courses]
        self.assertEqual(len(codes), len(set(codes)))
        refs = {r['course'] for n in self.nodes for r in n.get('rules', []) if 'course' in r}
        self.assertEqual(len(refs), 75)
        self.assertFalse(refs - set(codes))
        self.assertTrue(all(m['match_status'] == 'exact_course_code' for m in read('references/CMPSC_course_matches.json')['matches']))

    def test_technical_elective_list(self):
        node = next(n for n in self.nodes if n.get('id') == 'technical_electives')
        self.assertEqual(node['min_credits'], 18)
        self.assertEqual(len(node['rules']), 27)
        self.assertIn({'course': 'EE 456'}, node['rules'])

    def test_empty_pools_block_automatic_audit(self):
        for n in self.nodes:
            if n['type'] == 'POOL' and not n['rules']:
                self.assertTrue(n['requires_review'], n['label'])

    def test_provenance_resolves(self):
        ids = {s['id'] for s in read('references/CMPSC_sources.json')['sources']}
        for n in self.nodes:
            if 'source' in n:
                self.assertIn(n['source']['source_id'], ids)
        for c in read('courses.json')['courses']:
            if 'provenance' in c:
                self.assertIn(c['provenance']['source_id'], ids)

    def test_wrong_edition_rejected(self):
        with self.assertRaises(ValueError):
            parse_program('<html>2025-2026 edition</html>')

    def test_department_uses_current_credits_with_conflict(self):
        match = match_requirement('Department List', '2026-27', 'UP')
        self.assertEqual(match['requirement']['min_credits'], 6)
        self.assertEqual(match['status'], 'concept_match_credit_conflict')
        self.assertFalse(match['automatic_application_allowed'])

    def test_supporting_is_not_science(self):
        for label in ['Supporting Courses', 'Supportive Electives']:
            match = match_requirement(label, '2026-27', 'UP')
            self.assertIsNone(match['requirement'])
            self.assertEqual(match['status'], 'no_current_equivalent')

    def test_unknown_and_wrong_scope_fail_closed(self):
        self.assertEqual(match_requirement('List A', '2026-27', 'UP')['status'], 'unmatched')
        for year, campus in [('2024-25', 'UP'), ('2026-27', 'Beaver')]:
            self.assertEqual(match_requirement('Department List', year, campus)['status'], 'unsupported_catalog_or_campus')

    def test_historical_restrictions_not_active(self):
        ref = read('references/CMPSC_handbook_crosswalk.json')
        self.assertFalse(ref['automatic_application_allowed'])
        self.assertFalse(ref['historical_department_restrictions']['applies_to_target_catalog'])
        self.assertNotIn('historical_department_restrictions', self.program)


if __name__ == '__main__':
    unittest.main()
