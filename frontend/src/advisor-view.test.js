import test from 'node:test';
import assert from 'node:assert/strict';
import {viewTitle,reportStatus,prerequisiteRows,planProgress,seasonLabel,officialUrl,progressView,progressLabel} from './advisor-view.js';

test('screen titles and seasons fall back safely',()=>{
 assert.equal(viewTitle({type:'course_detail'}),'Course details');assert.equal(viewTitle({type:'unknown'}),'Advisor view');assert.equal(viewTitle(null),'Advisor view');
 assert.equal(seasonLabel('FA'),'Fall');assert.equal(seasonLabel('WI'),'WI');
});
test('report status labels keep the grade and never invent completion',()=>{
 assert.deepEqual(reportStatus('recorded:B+'),{key:'done',label:'In your report',grade:'B+'});
 assert.equal(reportStatus('in_progress').key,'progress');assert.equal(reportStatus('no').key,'missing');assert.equal(reportStatus(undefined).key,'missing');
 assert.equal(reportStatus('generic').key,'generic');
});
test('prerequisite trees keep one-of groups visible',()=>{
 const rows=prerequisiteRows({type:'ALL',result:'not_met',children:[
  {type:'ANY',result:'not_met',children:[{type:'COURSE',course:'CMPSC 122',result:'not_met'},{type:'COURSE',course:'CMPSC 132',result:'not_met'}]},
  {type:'COURSE',course:'CMPSC 360',result:'met',min_grade:'C',matched_record:{grade:'B'}},{type:'TEXT',result:'unknown',unparsed_text:'permission'}]});
 assert.deepEqual(rows.map(r=>r.group||r.course||r.text),['ALL','ANY','CMPSC 122','CMPSC 132','CMPSC 360','permission']);
 assert.equal(rows[2].depth,2);assert.equal(rows[4].grade,'B');assert.equal(rows[4].minGrade,'C');
 assert.deepEqual(prerequisiteRows(null),[]);
});
test('plan progress counts named courses only',()=>{
 const plan={terms:[{courses:[{inReport:'recorded:A'},{inReport:'in_progress'},{inReport:'not_in_report'},{inReport:'generic'}]}]};
 assert.deepEqual(planProgress(plan),{done:1,progress:1,total:3});assert.deepEqual(planProgress(null),{done:0,progress:0,total:0});
});
test('only https Penn State links are rendered as official',()=>{
 assert.equal(officialUrl('https://bulletins.psu.edu/x'),'https://bulletins.psu.edu/x');
 assert.equal(officialUrl('http://bulletins.psu.edu/x'),null);assert.equal(officialUrl('https://psu.edu.evil.com/'),null);assert.equal(officialUrl(undefined),null);
});
test('live progress shows the current step and the finished ones once',()=>{
 assert.deepEqual(progressView([]),{current:'Thinking about your question…',done:[]});
 const steps=[{kind:'llm',detail:'plan'},{kind:'tool',detail:'check_prerequisites'},{kind:'tool',detail:'check_prerequisites'},{kind:'llm',detail:'plan'},{kind:'tool',detail:'web_search'},{kind:'llm',detail:'answer'}];
 assert.deepEqual(progressView(steps),{current:'Writing the answer…',done:['Checking prerequisites…','Searching the web…']});
 assert.equal(progressView([{kind:'tool',detail:'web_search'}]).current,'Searching the web…');
 assert.equal(progressLabel({kind:'fallback',detail:'gemini'}),'Switching to the backup model…');assert.equal(progressLabel({kind:'tool',detail:'unknown_tool'}),'Looking something up…');
});
