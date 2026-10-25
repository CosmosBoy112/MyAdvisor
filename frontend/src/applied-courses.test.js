import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createSSRApp} from 'vue';
import {renderToString} from '@vue/server-renderer';
import {locale} from './i18n.js';
test('applied courses show verified earned and IP records, not suggested courses',async()=>{
 const file=new URL('./components/AppliedCourses.vue',import.meta.url),generated=new URL(`./components/.applied-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(file,'utf8'));await writeFile(generated,compileScript(descriptor,{id:'applied-test',inlineTemplate:true}).content);
 try{
  const {default:Component}=await import(generated.href);
  const item={status:'in_progress',appliedCoursesVerified:true,courses:['UNVERIFIED OPTION'],appliedCourses:[{code:'CMPSC 131',title:'Programming',term:'FA 2025',credits:3,grade:'A',status:'completed',page:2},{code:'CMPSC 132',title:'Programming II',term:'SP 2026',credits:3,grade:'IP',status:'in_progress',page:3}]};
  const html=await renderToString(createSSRApp(Component,{item}));for(const text of ['CMPSC 131','Programming','FA 2025','In progress / planned','PDF page'])assert.ok(html.includes(text));assert.ok(!html.includes('UNVERIFIED OPTION'));
  locale.value='ko';const ko=await renderToString(createSSRApp(Component,{item:{status:'complete',appliedCoursesVerified:false,appliedCourses:item.appliedCourses}}));assert.ok(ko.includes('확인된 과목별 내역이 없습니다'));assert.ok(!ko.includes('CMPSC 131'));
 }finally{locale.value='en';await unlink(generated);}
});
