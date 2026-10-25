import json
import unittest
from pathlib import Path
from load_course_restrictions import parse

DATA=Path(__file__).resolve().parents[1]/'data/catalog/2026-27'

class CourseRestrictionTests(unittest.TestCase):
 def test_standing_wording(self):
  self.assertEqual(parse('Enforced Prerequisite at Enrollment: third-semester standing'),[('min_semester',3,'third-semester standing')])
  self.assertEqual(parse('Enforced Prerequisite at Enrollment: 5th semester standing NUTR 358'),[('min_semester',5,'5th semester standing')])
  self.assertEqual(parse('ENGL 15 or CAS 137H and 4th Semester'),[('min_semester',4,'4th Semester')])
  self.assertEqual(parse('STAT 250 and Junior standing'),[('min_semester',5,'Junior standing')])
  self.assertEqual(parse('Senior standing'),[('min_semester',7,'Senior standing')])
 def test_uncheckable_wording_is_kept_verbatim(self):
  self.assertEqual(parse('Enforced Prerequisite at Enrollment: Prior approval of proposed assignment by instructor'),[('text',None,'Prior approval of proposed assignment by instructor')])
 def test_not_restrictions(self):
  for text in ['PSYCH 100 or STAT 200 or Graduate Status','satisfactory performance on the mathematics placement examination','3 credits in ECON','CMPSC 132 and CMPSC 360','',None]:
   self.assertEqual(parse(text),[],text)
 def test_recommended_preparation_is_not_enforced(self):
  self.assertEqual(parse('Enforced Prerequisite at Enrollment: STAT 200. Recommended Preparation: junior standing'),[])
 def test_2026_27_seed(self):
  courses={c['code']:parse(c.get('prereq_text') or '') for c in json.loads((DATA/'courses.json').read_text())['courses']}
  found={code:rows for code,rows in courses.items() if rows}
  # ENGL 202H came with the EECS majors; like ENGL 202C it needs fourth-semester standing.
  self.assertEqual(sorted(found),['BLAW 243','ENGL 202C','ENGL 202H','FDSC 404','FIN 100','IB 303','IST 495','NUTR 410'])
  self.assertEqual(sum(len(r) for r in found.values()),8)

if __name__=='__main__':unittest.main()

