import json
import unittest
from pathlib import Path
from load_eecs_db import CODES, bundle, planned_node
from crawl_eecs_catalog import walk

DATA=Path(__file__).resolve().parents[1]/'data/catalog/2026-27'

def load(code):return json.loads((DATA/'programs'/f'{code}.json').read_text())

class EecsCatalogTests(unittest.TestCase):
 def test_programs_and_credit_summaries(self):
  cmpen,ee=load('CMPEN_BS'),load('EE_BS')
  self.assertEqual((cmpen['min_total_credits'],cmpen['credit_summary']['major_min'],cmpen['credit_summary']['gened_major_overlap']),(127,106,24))
  self.assertEqual((ee['min_total_credits'],ee['credit_summary']['major_min'],ee['credit_summary']['gened_major_overlap']),(127,109,27))
  self.assertEqual(load('CMPEN_ETM')['program']['entrance_for'],'CMPEN_BS')
  self.assertEqual([n['min_gpa'] for n in load('CMPEN_ETM')['requirements'] if n['type']=='GPA_MIN'],[3.2])
  self.assertEqual([n['min_gpa'] for n in load('EE_ETM')['requirements'] if n['type']=='GPA_MIN'],[2.6])
 def test_footnoted_courses_have_no_grade_minimum(self):
  for code,course in [('CMPEN_BS','CMPSC 122'),('CMPEN_BS','CMPEN 275'),('EE_BS','CMPEN 275')]:
   leaves=[n for n in walk(load(code)['requirements']) if n.get('rules')==[{'course':course}]]
   self.assertTrue(leaves,course);self.assertTrue(all('min_grade' not in n for n in leaves),(code,course))
 def test_course_pairs_and_alternatives_keep_their_structure(self):
  ee=load('EE_BS')['requirements']
  calc=next(n for n in walk(ee) if n['label']=='MATH 231 + MATH 232 alternatives')
  self.assertEqual(calc['type'],'GROUP_ANY');self.assertEqual(calc['children'][0]['type'],'GROUP_ALL');self.assertEqual(calc['children'][1]['rules'],[{'course':'MATH 230'}])
  digital=next(n for n in walk(load('CMPEN_BS')['requirements']) if n['label']=='Select 4 credits')
  self.assertEqual(digital['type'],'GROUP_ANY');self.assertEqual([c['type'] for c in digital['children']],['POOL','GROUP_ALL'])
 def test_unlisted_approved_lists_require_review(self):
  pools=[n for n in walk(load('EE_BS')['requirements']) if n['type']=='POOL' and not n.get('rules')]
  self.assertEqual(len(pools),5);self.assertTrue(all(n.get('requires_review') for n in pools))
 def test_loader_bundle_and_every_course_is_present(self):
  docs,policies,courses,upgraded=bundle(DATA)
  for doc in docs:[planned_node(n) for n in doc['requirements']]
  self.assertEqual(sorted({p[0] for p in policies}),sorted(CODES))
  self.assertIn(('CMPEN_BS','suggested_academic_plan.DEFAULT'),{(p[0],p[1]) for p in policies})
  known={c['code']:c for c in json.loads((DATA/'courses.json').read_text())['courses']}
  wanted={r['course'] for doc in docs for n in walk(doc['requirements']) for r in n.get('rules',[]) if 'course' in r}
  self.assertFalse([c for c in wanted if not known.get(c,{}).get('title')],'every program course has a title and credits')
  self.assertEqual(sorted(upgraded),['CAS 137H','CAS 138T','IE 424','STAT 401'])

if __name__=='__main__':unittest.main()
