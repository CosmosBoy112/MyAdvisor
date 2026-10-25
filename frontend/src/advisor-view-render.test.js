import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createSSRApp} from 'vue';
import {renderToString} from '@vue/server-renderer';
import {locale} from './i18n.js';
// Server-built screens (AdvisorViews.java shapes) render their verified data and never link to non-PSU or unsafe URLs.
test('advisor screens render server data for each view type',async()=>{
 const path=new URL('./components/AdvisorView.vue',import.meta.url),generated=new URL(`./components/.advisor-view-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(path,'utf8'));
 // MinorPlanner has its own render test; a stub keeps this one independent of it.
 const code=compileScript(descriptor,{id:'advisor-view-test',inlineTemplate:true}).content.replace("import MinorPlanner from './MinorPlanner.vue'","const MinorPlanner={props:['assessment'],template:'',render(){return 'MINOR:'+this.assessment.programCode}}");
 await writeFile(generated,code);
 try{
  const {default:View}=await import(generated.href);
  const render=view=>renderToString(createSSRApp(View,{view}));
  const detail=await render({type:'course_detail',warnings:['Enrollment restrictions were not checked.'],data:{courses:[
   {code:'CMPSC 465',course:{title:'Data Structures and Algorithms',credits:'3.0',source_url:'https://bulletins.psu.edu/cmpsc/',unlocks:['CMPSC 469']},
    prerequisites:{status:'not_met',prerequisite_text:'(CMPSC 122 or CMPSC 132) and CMPSC 360',tree:{type:'ALL',result:'not_met',children:[{type:'ANY',result:'not_met',children:[{type:'COURSE',course:'CMPSC 122',result:'not_met'}]},{type:'COURSE',course:'CMPSC 360',result:'met',matched_record:{grade:'B'}}]}}},
   {code:'XYZ 999',status:'not_in_catalog_database'}]}});
  for(const text of ['Course details','Data Structures and Algorithms','Prerequisites not met','One of:','CMPSC 122','CMPSC 360','· B','CMPSC 469','Not in the catalog database','Enrollment restrictions were not checked.'])assert.ok(detail.includes(text),text);
  assert.ok(detail.includes('href="https://bulletins.psu.edu/cmpsc/"'));
  const options=await render({type:'course_options',data:{courses:[{code:'CMPSC 465',title:'DSA',credits:'3',prerequisiteStatus:'in_progress',inReport:'no',source_url:'javascript:alert(1)'},{code:'DS 200',prerequisiteStatus:'met',inReport:'recorded:A'}]}});
  for(const text of ['Met once current courses are passed','Not in your report','In your report','· A'])assert.ok(options.includes(text),text);
  assert.ok(!options.includes('javascript:'),'unsafe links are not rendered');
  const plan=await render({type:'semester_plan',data:{status:'reference_plan',program:'Data Sciences, B.S.',option:'COMPUTATIONAL',totalCredits:123,sourceUrl:'https://bulletins.psu.edu/ds/',terms:[{year:1,season:'FA',totalCredits:15,courses:[{label:'CMPSC 121 or 131',credits:3,inReport:'recorded:A'},{label:'General Education Course',credits:3,inReport:'generic'}]}]}});
  for(const text of ['Suggested semester plan','Year 1 · Fall','1 / 1','Choose a course'])assert.ok(plan.includes(text),text);
  const unavailable=await render({type:'semester_plan',data:{status:'unavailable',message:'No suggested semester plan is loaded.'}});
  assert.ok(unavailable.includes('No suggested semester plan is loaded.'));assert.ok(!unavailable.includes('Year'));
  const gap=await render({type:'requirement_gap',data:{requirements:[],courseOptions:[{node_id:4,label:'List A',course_list:'not_listed_in_database',courses:[]}]}});
  assert.ok(gap.includes('Every requirement in your report is completed'));assert.ok(gap.includes('approved course list is not in the database'));
  assert.ok((await render({type:'minor_check',data:{programCode:'MATH_UMNR'}})).includes('MINOR:MATH_UMNR'));
  locale.value='ko';assert.ok((await render({type:'requirement_gap',data:{requirements:[{title:'List A',required:6,completed:3}]}})).includes('남은 요건'));
 }finally{locale.value='en';await unlink(generated);}
});
