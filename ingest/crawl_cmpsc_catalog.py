"""Crawl official UP CMPSC sources; keep older handbook guidance outside active rules.

Requires lxml and pypdf. Source downloads are cached outside the catalog outputs.
Run: python ingest/crawl_cmpsc_catalog.py --cache /tmp/cmpsc-sources --out data/catalog/2026-27
--offline uses only cached files; --refresh explicitly re-downloads sources.
"""
from __future__ import annotations
import argparse, copy, hashlib, json, re, ssl, time
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urlparse, urljoin
from urllib.request import Request, urlopen
from lxml import html
from pypdf import PdfReader
from parse_bulletin_courses import parse_block, build_course

BULLETIN='https://bulletins.psu.edu/undergraduate/colleges/engineering/computer-science-bs/'
INDEX='https://www.eecs.psu.edu/Students/undergraduate/Computer-Science.aspx'
YEAR='2026-27'

def clean(value): return ' '.join(value.split())
def dump(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n')

def expanded_codes(label):
    label=clean(label)
    m=re.fullmatch(r'([A-Z]+(?:/[A-Z]+)*) (\d+[A-Z]*)',label)
    if not m: raise ValueError(f'Unrecognized course identifier: {label}')
    return [s+' '+m[2] for s in m[1].split('/')]

def credit_range(value):
    m=re.fullmatch(r'(\d+(?:\.\d+)?)(?:-(\d+(?:\.\d+)?))?',value)
    if not m: raise ValueError(f'Unrecognized credits: {value}')
    return float(m[1]),float(m[2] or m[1])

def source(section, row=None):
    result={'source_id':'bulletin_cmpsc_2026_27','url':BULLETIN+'#'+section+'text','catalog_year':YEAR}
    if row is not None: result['table_row']=row
    return result

def course_node(labels, grade, row):
    # Multiple links in one row mean AND. Slash-separated subjects are cross-list alternatives.
    children=[]
    for label in labels:
        child={'label':label,'type':'POOL','min_count':1,'rules':[{'course':c} for c in expanded_codes(label)],'source':source('programrequirements',row)}
        if grade:child['min_grade']=grade
        children.append(child)
    if len(children)==1:return children[0]
    return {'label':' + '.join(labels),'type':'GROUP_ALL','children':children,'source':source('programrequirements',row)}

def parse_program(raw):
    doc=html.fromstring(raw)
    full=clean(doc.text_content())
    if '2026-2027 edition' not in full: raise ValueError('Bulletin edition changed; do not overwrite the 2026-27 catalog')
    req=doc.get_element_by_id('programrequirementstextcontainer')
    text=clean(req.text_content())
    total=int(re.search(r'minimum of (\d+) credits is required',text)[1])
    table=req.xpath('.//table[contains(@class,"sc_courselist")]')[0]
    groups=[];group=None;grade=None;selection=None;last=None
    for rownum,row in enumerate(table.xpath('.//tr')):
        cells=row.xpath('./td')
        if not cells:continue
        words=clean(row.text_content());classes=row.get('class','').split()
        labels=[clean(a.get('title') or a.text_content()) for a in row.xpath('.//a[contains(@class,"code")]')]
        hours=row.xpath('./td[contains(@class,"hourscol")]');credits=clean(hours[0].text_content()) if hours else ''
        if 'areaheader' in classes:
            group={'id':{'Prescribed Courses':'prescribed','Additional Courses':'additional','Supporting Courses and Related Areas':'supporting_and_related'}[clean(cells[0].text_content())],'label':clean(cells[0].text_content()),'type':'GROUP_ALL','children':[],'source':source('programrequirements',rownum)}
            groups.append(group);grade=None;selection=None;last=None;continue
        if 'areasubheader' in classes:
            if 'C or better' not in words:raise ValueError('Unrecognized grade header')
            grade='C';selection=None;last=None;continue
        if labels:
            n=course_node(labels,grade,rownum)
            if selection:
                if selection['id']=='technical_electives':
                    selection['rules'].extend(n['rules']);selection['source_rows'].append(rownum)
                elif selection['id']=='natural_science':selection['children'].append(n)
                else:raise ValueError('Unexpected course under selection')
            elif 'orclass' in classes:
                if last is None:raise ValueError('Orphan OR row')
                if last['type']!='GROUP_ANY':
                    old=copy.deepcopy(last)
                    last.clear();last.update({'label':old['label']+' alternatives','type':'GROUP_ANY','min_count':1,'children':[old],'source':old['source']})
                last['children'].append(n)
            else:
                if credits:n['credits_range']=list(credit_range(credits))
                group['children'].append(n);last=n
            continue
        if 'First-Year Seminar' in words:
            n={'id':'first_year_seminar','label':'First-Year Seminar','type':'POOL','min_credits':credit_range(credits)[0],'rules':[{'attribute':'FYS'}],'source':source('programrequirements',rownum)}
            group['children'].append(n);last=n;selection=None
        elif re.search(r'Select \d+ credits from the following',words):
            selection={'id':'technical_electives','label':'Computer Science technical electives','type':'POOL','min_credits':credit_range(credits)[0],'rules':[],'source_rows':[],'source':source('programrequirements',rownum)}
            group['children'].append(selection);last=None
        elif re.search(r'Select \d+-\d+ credits from the following',words):
            selection={'id':'natural_science','label':'Natural science elective','type':'GROUP_ANY','min_count':1,'credits_range':list(credit_range(credits)),'children':[],'source':source('programrequirements',rownum)}
            group['children'].append(selection);last=None
        elif 'approved list of natural sciences' in words:
            if not selection or selection['id']!='natural_science':raise ValueError('Unexpected science list')
            selection['children'].append({'id':'approved_natural_science','label':'Approved natural science list','type':'POOL','min_credits':3,'rules':[],'requires_review':True,'review':['Approved list is referenced but not enumerated in the current Bulletin. Do not interpret an empty list as any GN course.'],'source':source('programrequirements',rownum)})
        elif 'department list' in words.lower():
            group['children'].append({'id':'department_list','label':'Department List','type':'POOL','min_credits':credit_range(credits)[0],'rules':[],'requires_review':True,'review':['Current eligibility list needs adviser confirmation; older handbook exclusions are reference-only.'],'source':source('programrequirements',rownum)})
            selection=None;last=None
        else:raise ValueError(f'Unrecognized requirement row {rownum}: {words}')
    # Do not confuse University Park ETM with the 2.60 GPA at other campuses.
    etm=doc.get_element_by_id('howtogetintextcontainer')
    etext=clean(etm.text_content()).split('Beaver, Brandywine, Hazleton')[0]
    gpa=float(re.search(r'GPA\) of ([\d.]+)',etext)[1])
    window=[int(x) for x in re.search(r'(\d+)-(\d+) graded Penn State credits',etext).groups()]
    up=etm.xpath('.//ul')[0]
    reqcourses=up.xpath('.//li')[2]
    etmlabels=[clean(a.get('title') or a.text_content()) for a in reqcourses.xpath('.//a[contains(@class,"code")]')]
    if len(etmlabels)!=7:raise ValueError('ETM course layout changed')
    etmchildren=[]
    for alternatives in [etmlabels[:2],etmlabels[2:4],etmlabels[4:5],etmlabels[5:6],etmlabels[6:7]]:
        n={'label':' or '.join(alternatives),'type':'POOL','min_count':1,'min_grade':'C','rules':[{'course':c} for label in alternatives for c in expanded_codes(label)],'source':source('howtogetin')}
        etmchildren.append(n)
    requirements=groups+[{'id':'graduation_gpa','label':'Graduation GPA','type':'GPA_MIN','min_gpa':2.0,'source':source('programrequirements')}]
    # GenEd includes breadth/overlap constraints that this draft schema cannot enforce alone.
    gened=[('GQ',6),('GWS',9),('GA',3),('GHW',3),('GH',3),('GS',3),('GN',3),('INTER',6)]
    gened_nodes=[]
    for attr,credits in gened:
        n={'label':attr,'type':'POOL','min_credits':credits,'exclusive':False,'rules':[{'attribute':attr}],'source':source('programrequirements')}
        if attr in ['GQ','GWS']:n['min_grade']='C'
        if attr!='INTER':n.update(requires_review=True,review=['Foundation and breadth slots exclude Inter-Domain courses; the draft attribute filter alone cannot enforce this.'])
        gened_nodes.append(n)
    gened_nodes.append({'label':'Exploration','type':'POOL','min_credits':9,'rules':[],'requires_review':True,'review':['3 GN credits plus 6 exploration credits; respect permitted attributes, language threshold, and non-double-counting.'],'source':source('programrequirements')})
    requirements.append({'id':'general_education','label':'General Education','type':'GROUP_ALL','children':gened_nodes,'source':source('programrequirements')})
    for attr in ['US','IL','WAC']:
        requirements.append({'id':attr.lower(),'label':attr,'type':'POOL','min_credits':3,'exclusive':False,'rules':[{'attribute':attr}],'source':source('programrequirements')})
    program={'program':{'code':'CMPSC_BS','name':'Computer Science, B.S. (Engineering)','type':'major','college':'Engineering'},'campus':'UP','catalog_year':YEAR,'min_total_credits':total,'source_urls':[BULLETIN],'status':'partial_requires_review','options':[],'notes':['Applies to University Park, entry year 2026-27. CSENG_BS and other campuses are not interchangeable.','24 GenEd credits overlap major requirements; 21 GenEd credits must be distinct. Never sum all nodes as independent credits.','Credit totals do not prove graduation eligibility. Unresolved pools and extra constraints must block a fully satisfied audit.','The linked older handbook is not an authority for automatic 2026-27 substitutions.'],'credit_summary':{'general_education':45,'major_min':106,'major_max':108,'gened_major_overlap':24,'distinct_gened':21},'requirements':requirements}
    entrance={'program':{'code':'CMPSC_ETM','name':'Computer Science Entrance to Major (University Park)','type':'entrance','college':'Engineering','entrance_for':'CMPSC_BS'},'campus':'UP','catalog_year':YEAR,'source_urls':[BULLETIN+'#howtogetintext'],'eligibility':{'college_any_of':['Engineering','Division of Undergraduate Studies'],'graded_penn_state_credits_min':window[0],'graded_penn_state_credits_max':window[1],'excluded_credit_sources':['transfer','AP'],'entry_terms':['Summer 2026','Fall 2026','Spring 2027']},'requirements':[{'label':'Entrance courses','type':'GROUP_ALL','children':etmchildren},{'label':'Entrance cumulative GPA','type':'GPA_MIN','min_gpa':gpa,'source':source('howtogetin')}],'requires_review':True,'notes':['The draft requirement tree cannot enforce graded-credit origin or current college; evaluate eligibility fields separately.']}
    return program,entrance

class Fetcher:
    def __init__(self,cache,offline=False,refresh=False):
        self.cache=cache;self.offline=offline;self.refresh=refresh;self.sources=[]
        cache.mkdir(parents=True,exist_ok=True)
        self.context=ssl.create_default_context(cafile='/etc/ssl/cert.pem' if Path('/etc/ssl/cert.pem').exists() else None)
    def get(self,name,url):
        host=urlparse(url).hostname
        if host not in ['bulletins.psu.edu','www.eecs.psu.edu']:raise ValueError('Only known official source hosts are allowed')
        dest=self.cache/name
        if not dest.exists() or self.refresh:
            if self.offline:raise FileNotFoundError(dest)
            with urlopen(Request(url,headers={'User-Agent':'BLS-MyAdvisor-Catalog/1.0 (academic source indexing)'}),context=self.context,timeout=40) as r:
                if urlparse(r.url).hostname not in ['bulletins.psu.edu','www.eecs.psu.edu','undergraduate.bulletins.psu.edu']:raise ValueError('Unexpected source redirect')
                dest.write_bytes(r.read())
            time.sleep(.15)
        data=dest.read_bytes()
        source_id = {'bulletin.html':'bulletin_cmpsc_2026_27','handbook-index.html':'handbook_index','handbook.pdf':'handbook_2024_25'}.get(name, 'course_'+name.removeprefix('c_').removesuffix('.html')+'_2026_27')
        self.sources.append({'id':source_id,'url':url,'cache_file':name,'sha256':hashlib.sha256(data).hexdigest(),'downloaded_at':datetime.fromtimestamp(dest.stat().st_mtime,timezone.utc).isoformat()})
        return data

def handbook_reference(fetch):
    index=html.fromstring(fetch.get('handbook-index.html',INDEX))
    links=[]
    for a in index.xpath('//a[@href]'):
        label=clean(a.text_content())
        if re.fullmatch(r'20\d\d-\d\d',label) and 'handbook' in a.get('href').lower():links.append((label,urljoin(INDEX,a.get('href'))))
    if not links:raise ValueError('No dated undergraduate handbook links found')
    year,url=max(links)
    # Reviewed mapping below only covers this PDF; do not silently reuse it for a new handbook.
    if year!='2024-25':raise ValueError('New handbook available; review and update crosswalk before import')
    fetch.get('handbook.pdf',url)
    reader=PdfReader(fetch.cache/'handbook.pdf');pages=[clean(p.extract_text()) for p in reader.pages]
    if len(pages)!=19 or '2024' not in re.sub(r'\s+','',pages[0]):raise ValueError('Handbook layout/version changed')
    for page,phrase in [(14,'Supporting Courses (6 credits)'),(16,'Department List (General Elective) Guidelines (10-14 credits)'),(17,'Summer 2021')]:
        if phrase not in pages[page]:raise ValueError('Handbook mapping needs re-review')
    oldcore=' '.join(pages[10:12]).split('Computer Science Electives')[0]
    oldcodes=sorted(set(re.findall(r'(?:o |OR )((?:CMPSC|CMPEN) \d+[A-Z]?)',oldcore)))
    excluded={
      'ASTRO':['1','6','7N','10','11','120','140'],'BISC':['1','2','3','4'],'CHEM':['1','3','101','108'],
      'CAS':['126','283'],'CMPSC':['100','101','200','201','203'],'CYBER':['100'],'EMSC':['150'],'EE':['465'],'ESL':['4'],
      'IST':['140','210','220','230','240','242','261','311','361'],'LLED':['5','10'],'MATH':['200','201'],'PHIL':['12'],
      'PHSC':['7'],'PHYS':['250','251'],'STS':['150'],'STAT':['401','487']}
    crosswalk={
     'program_code':'CMPSC_BS','campus':'UP','target_catalog_year':YEAR,'handbook_year':year,'handbook_url':url,
     'status':'reference_only_year_mismatch','automatic_application_allowed':False,
     'warnings':['Cover/link says 2024-25, but page 18 describes entry in Summer 2021 through Spring 2022. Cohort applicability needs confirmation.','No 2026-27 undergraduate handbook was linked on the official EECS page when collected.','Bulletin suggested-plan footnotes still refer to a handbook; do not import old totals or exclusions as current rules.'],
     'historical_core_courses':oldcodes,
     'mappings':[
      {'id':'department_list','report_aliases':['Department List','General Elective','General Electives','Approved Free Electives'],'bulletin_requirement_id':'department_list','bulletin_credits':6,'handbook_credits_range':[10,14],'handbook_pages':[17,18],'match_status':'concept_match_credit_conflict','requires_review':True,'summary':'Same category name, different required credits; current course eligibility remains unresolved.'},
      {'id':'legacy_supporting','report_aliases':['Supporting Courses','Supporting Electives','Supportive Electives','Supportive elective'],'bulletin_requirement_id':None,'handbook_credits':6,'handbook_pages':[15,16],'match_status':'no_current_equivalent','requires_review':True,'summary':'Older thematic advanced electives are not the current natural-science choice or Department List.'},
      {'id':'technical_electives','report_aliases':['Computer Science Electives','CMPSC Elective','CMPSC/CMPEN 400-Level Elective','Technical Electives'],'bulletin_requirement_id':'technical_electives','bulletin_credits':18,'handbook_credits':12,'handbook_pages':[12,13],'match_status':'structure_changed','requires_review':True,'summary':'Use the current enumerated 18-credit pool; the older writing/elective split is not interchangeable.'},
      {'id':'writing','report_aliases':['Writing requirement','Writing Across the Curriculum'],'bulletin_requirement_id':'wac','handbook_pages':[12],'match_status':'course_choice_changed','requires_review':True,'summary':'The current prescribed list includes CMPSC 483W. Older writing-course choices do not imply an automatic substitution.'},
      {'id':'natural_science','report_aliases':['Natural Science Elective','Natural Science (GN) Elective'],'bulletin_requirement_id':'natural_science','bulletin_credits_range':[2,3],'handbook_pages':[14],'match_status':'current_bulletin_only','requires_review':True,'summary':'PHYS 213 or PHYS 214 are explicit choices; the other approved-list branch needs confirmation.'},
      {'id':'language','report_aliases':['Foreign Language Proficiency','Language Proficiency'],'bulletin_requirement_id':None,'handbook_credits':4,'handbook_pages':[16],'match_status':'no_current_equivalent','requires_review':True,'summary':'Do not add the old separate four-credit language requirement to the current program.'}
     ],
     'historical_department_restrictions':{'applies_to_target_catalog':False,'source_pages':[17,18],'excluded_courses':[s+' '+n for s,ns in excluded.items() for n in ns]+[s+' '+n for s in ['STAT','MATH'] for n in ['318','319','414','415','418']],'excluded_ranges':[{'subject':'MATH','below':140},{'subject':'PHYS','below':211},{'subject':'STAT','below':318,'except':['STAT 200']}],'credit_caps':{'additional_physical_education':3,'cooperative_education':3,'engineering_internship':2,'CHEM 106':3},'manual_checks':['Degree applicability','Course intended exclusively for non-science/non-technical students','Remedial content or duplication of prior courses','Two-credit MATH 140A/CHEM 106 exclusion; CHEM 106 cap is not blanket approval']},
     'historical_supporting_restrictions':{'applies_to_target_catalog':False,'source_pages':[15,16],'min_credits':6,'level_min':400,'level_max':499,'excluded_subjects':['CMPSC','CMPEN','DS'],'manual_checks':['A coherent theme supporting CS is required; matching subject alone is insufficient.','Independent study is excluded. Nonautomatic audit placement needs a petition.','STAT/MATH 414,415,418 and CMPSC-crosslisted MATH 451,455,456,467 are excluded.','Special-topic or independent-course ranges require individual review; no blanket eligibility.']}
    }
    return crosswalk

def walk(nodes):
    for n in nodes:
        yield n
        yield from walk(n.get('children',[]))

def collect_courses(program,fetch,out):
    wanted={r['course'] for n in walk(program['requirements']) for r in n.get('rules',[]) if 'course' in r}
    subjects=sorted({c.split()[0].lower() for c in wanted})
    blocks={}
    for subject in subjects:
        raw=fetch.get('c_'+subject+'.html',f'https://bulletins.psu.edu/university-course-descriptions/undergraduate/{subject}/').decode()
        if '2026-27 edition of the University Bulletins' not in clean(html.fromstring(raw).text_content()):
            raise ValueError(f'Course page edition changed: {subject}')
        for block in raw.split('<div class="courseblock">')[1:]:
            code,title,credits,paras=parse_block(block)
            blocks[code]=(title,credits,paras,subject)
    existing=json.loads((out/'courses.json').read_text()) if (out/'courses.json').exists() else {'catalog_year':YEAR,'courses':[]}
    if existing['catalog_year']!=YEAR:raise ValueError('Wrong course catalog year')
    bycode={c['code']:c for c in existing['courses']};matches=[];new=0
    for code in sorted(wanted):
        if code in blocks:
            title,credits,paras,subject=blocks[code];c=build_course(code,title,credits,paras,subject)
            c['description']=None # Keep factual catalog fields; do not republish full descriptions.
            c['catalog_year']=YEAR;c['provenance']={'source_id':'course_'+subject+'_2026_27','collected_for':'CMPSC_BS'}
            if code not in bycode:new+=1
            bycode[code]=c
            matches.append({'code':code,'match_status':'exact_course_code','course_status':c['status'],'source_url':c['source_url'],'review':c['review']})
        else:
            # Existing seed isn't evidence of current presence; do not silently call it verified.
            matches.append({'code':code,'match_status':'missing_from_fetched_course_page','requires_review':True})
            if code not in bycode:
                subject,number=code.split();bycode[code]={'code':code,'subject':subject,'number':number,'level':int(re.match(r'\d+',number)[0])//100*100,'title':None,'credits_min':None,'credits_max':None,'repeatable':False,'description':None,'attributes':[],'crosslists':[],'prereq_text':None,'prereq':None,'status':'not_in_course_list','review':['Present in program but missing from current subject page.'],'source_url':None}
    # Ensure all prerequisite/cross-list foreign-key references exist, without guessing their eligibility.
    refs=set()
    def references(n):
        if n.get('course'):refs.add(n['course'])
        for child in n.get('children',[]):references(child)
    for code in wanted:
        c=bycode[code];references(c.get('prereq') or {});refs.update(c.get('crosslists',[]))
    for code in sorted(refs-set(bycode)):
        if code in blocks:
            c=build_course(code,*blocks[code]);c.update(description=None,prereq=None,crosslists=[],status='reference_only',review=['Reference-only row; prerequisite expansion intentionally stops here.'])
        else:
            subject,number=code.split();c={'code':code,'subject':subject,'number':number,'level':int(re.match(r'\d+',number)[0])//100*100,'title':None,'credits_min':None,'credits_max':None,'repeatable':False,'description':None,'attributes':[],'crosslists':[],'prereq_text':None,'prereq':None,'status':'reference_only','review':['Referenced course page not collected.'],'source_url':None}
        bycode[code]=c
    original_order=[c['code'] for c in existing['courses']]
    existing['courses']=[bycode[c] for c in original_order]+[bycode[c] for c in sorted(set(bycode)-set(original_order))]
    dump(out/'courses.json',existing)
    return {'program_code':'CMPSC_BS','catalog_year':YEAR,'matches':matches,'notes':['Course existence is distinct from elective eligibility. Prerequisites with review flags need manual confirmation.']}

def main():
    ap=argparse.ArgumentParser();ap.add_argument('--cache',type=Path,required=True);ap.add_argument('--out',type=Path,required=True);ap.add_argument('--offline',action='store_true');ap.add_argument('--refresh',action='store_true');a=ap.parse_args()
    if a.offline and a.refresh:ap.error('--offline and --refresh are mutually exclusive')
    fetch=Fetcher(a.cache,a.offline,a.refresh)
    program,entrance=parse_program(fetch.get('bulletin.html',BULLETIN));handbook=handbook_reference(fetch)
    match=collect_courses(program,fetch,a.out)
    # Distinguish current prescribed requirements from historical examples without inventing equivalences.
    current={r['course'] for n in walk(program['requirements'][:1]) for r in n.get('rules',[]) if 'course' in r}
    handbook['historical_core_not_current_prescribed']=sorted(set(handbook['historical_core_courses'])-current)
    dump(a.out/'programs/CMPSC_BS.json',program);dump(a.out/'programs/CMPSC_ETM.json',entrance)
    dump(a.out/'references/CMPSC_handbook_crosswalk.json',handbook);dump(a.out/'references/CMPSC_course_matches.json',match)
    dump(a.out/'references/CMPSC_sources.json',{'program_code':'CMPSC_BS','catalog_year':YEAR,'sources':fetch.sources})
    print(json.dumps({'program':'CMPSC_BS','campus':'UP','course_references':len(match['matches']),'unmatched':sum(m['match_status']!='exact_course_code' for m in match['matches']),'handbook':handbook['status']}))
if __name__=='__main__':main()
