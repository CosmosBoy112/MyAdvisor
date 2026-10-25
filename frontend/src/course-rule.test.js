import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createSSRApp} from 'vue';
import {renderToString} from '@vue/server-renderer';
import {locale} from './i18n.js';

test('course rules preserve nested all/any constraints and unknown approved pools',async()=>{
 const source=new URL('./components/CourseRule.vue',import.meta.url);
 const generated=new URL(`./components/.course-rule-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(source,'utf8'),{filename:'CourseRule.vue'});
 await writeFile(generated,compileScript(descriptor,{id:'course-rule-test',inlineTemplate:true}).content);
 try{
  const {default:Rule}=await import(generated.href);
  const rule={label:'Science',type:'GROUP_ANY',min_count:1,children:[
   {label:'Sequence',type:'GROUP_ALL',children:[{label:'First course',type:'POOL',min_count:1,min_grade:'C',rules:[{course:'PHYS 211'}]},{label:'Second course',type:'POOL',rules:[{course:'PHYS 212'}]}]},
   {label:'Approved alternative',type:'POOL',min_credits:3,rules:[],requires_review:true,review:['Approval required.']}
  ]};
  locale.value='en';
  const html=await renderToString(createSSRApp(Rule,{rule}));
  for(const text of ['Choose groups:','Complete all groups below','PHYS 211','PHYS 212','Minimum grade:','Approved course list unavailable','Approval required.'])assert.ok(html.includes(text),text);
  locale.value='ko';
  const ko=await renderToString(createSSRApp(Rule,{rule}));
  assert.ok(ko.includes('아래 모든 그룹 충족'));assert.ok(ko.includes('승인 과목 목록 미확인'));assert.ok(ko.includes('PHYS 211'));
 }finally{locale.value='en';await unlink(generated);}
});
