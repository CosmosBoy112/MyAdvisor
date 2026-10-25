import json
import unittest
from pathlib import Path
from crawl_gened_courses import compare, gened_courses, list_codes
from crawl_eecs_catalog import walk
from load_gened_db import planned_node
from parse_bulletin_courses import build_course

DATA=Path(__file__).resolve().parents[1]/'data/catalog/2026-27'

def block(code,title,attrs=(),extra=()):
 paras=''.join(f'<p>{p}</p>' for p in ('A description.',*extra,*attrs))
 return (f'<div class="courseblock"><div class="course_codetitle">{code}: {title}</div>'
         f'<div class="course_credits">3 Credits</div><div class="courseblockmeta">{paras}</div></div>')

class GenEdCatalogTests(unittest.TestCase):
 def test_keeps_only_courses_with_gened_or_university_attributes(self):
  page=''.join([block('ART 10','Intro',('General Education: Arts (GA)',)),
                block('ART 20','Studio'),
                block('ART 30N','Inter',('General Education: Arts (GA)','General Education: Humanities (GH)','General Education - Integrative: Interdomain')),
                block('ART 40','Writing',('Writing Across the Curriculum',)),
                block('ART TEMP','Placeholder')])
  kept,total,attrs,skipped=gened_courses([('art',page)])
  self.assertEqual((total,skipped),(5,['ART TEMP']))
  self.assertEqual([c['code'] for c in kept],['ART 10','ART 30N','ART 40'])
  self.assertEqual(attrs['ART 30N'],{'GA','GH','INTER'})
 def test_duplicate_course_codes_stop_the_crawl(self):
  with self.assertRaises(ValueError):gened_courses([('a',block('ART 10','A')),('b',block('ART 10','A'))])
 def test_course_list_codes_and_comparison(self):
  page=('<td class="codecol"><a href="/search/?P=ART%2010" title="ART&#160;10" class="bubblelink code">ART 10</a></td>'
        '<td class="codecol"><a href="/search/?P=ART%2050" title="ART 50" class="bubblelink code">ART 50</a></td>').replace('&#160;','\xa0')
  codes=list_codes(page);self.assertEqual(codes,{'ART 10','ART 50'})
  r=compare({'ART 10':{'GA'},'ART 30N':{'GA','INTER'}},{'GA':codes})['GA']
  self.assertEqual((r['only_on_list'],r['only_on_course_page']),(['ART 50'],['ART 30N']))
 def test_unbalanced_prerequisite_becomes_text(self):
  c=build_course('ART 10','A','3 Credits',['d','Enforced Prerequisite at Enrollment: (ART 1 or ART 2'],'art')
  self.assertEqual((c['status'],c['prereq']['type']),('text_only','TEXT'))
 def test_gened_tree_totals_and_exclusions(self):
  doc=json.loads((DATA/'programs'/'GENED.json').read_text())
  top={n['id']:n for n in doc['requirements']}
  self.assertEqual(sum(top[k]['min_credits'] for k in ('foundations','knowledge_domains','integrative_studies','exploration')),doc['min_total_credits'])
  for k in ('foundations','knowledge_domains','exploration'):
   self.assertEqual(sum(c['min_credits'] for c in top[k]['children']),top[k]['min_credits'],k)
  for n in top['foundations']['children']+top['knowledge_domains']['children']:
   self.assertIn({'attribute':'INTER','include':False},n['rules'],n['label'])
  self.assertTrue(all(not n.get('exclusive',True) for n in walk([top['additional_university']])))
  planned=[planned_node(n) for n in doc['requirements']]
  self.assertEqual(planned[0]['children'][0]['min_grade'],'C')

if __name__=='__main__':unittest.main()
