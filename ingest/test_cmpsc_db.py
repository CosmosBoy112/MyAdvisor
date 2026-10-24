import json
import unittest
from pathlib import Path
from load_cmpsc_db import bundle,planned_node,walk

DATA=Path(__file__).resolve().parents[1]/'data/catalog/2026-27'

class CmpscDbTests(unittest.TestCase):
 def test_scope_and_lossless_metadata(self):
  docs,policies=bundle(DATA)
  self.assertEqual([p['program']['code'] for p in docs],['CMPSC_BS','CMPSC_ETM'])
  self.assertEqual(len(policies),5)
  self.assertEqual(policies[0][2]['document'],docs[0])
  self.assertEqual(policies[1][2]['document']['eligibility']['graded_penn_state_credits_min'],29)
  self.assertFalse(policies[2][2]['automatic_application_allowed'])

 def test_unresolved_pools_keep_review_and_source(self):
  docs,_=bundle(DATA)
  nodes=list(walk(docs[0]['requirements']))
  dept=next(n for n in nodes if n.get('id')=='department_list')
  plan=planned_node(dept)
  self.assertEqual(plan['min_credits'],6)
  self.assertEqual(plan['rules'],[])
  meta=json.loads(plan['source_text'].split(': ',1)[1])
  self.assertTrue(meta['requires_review']);self.assertIn('source',meta)
  with self.assertRaises(ValueError):planned_node({'label':'unknown','type':'POOL','min_credits':3,'rules':[]})

 def test_compound_alternatives_and_crosslists_preserved(self):
  docs,_=bundle(DATA)
  source=next(n for n in walk(docs[0]['requirements']) if n['type']=='GROUP_ANY' and n['label'].startswith('CMPEN 270'))
  node=planned_node(source)
  self.assertEqual(node['children'][1]['node_type'],'GROUP_ALL')
  self.assertEqual([n['rules'][0]['course_code'] for n in node['children'][1]['children']],['CMPEN 271','CMPEN 275'])
  self.assertEqual(node['children'][1]['children'][0]['min_grade'],'C')

if __name__=='__main__':unittest.main()
