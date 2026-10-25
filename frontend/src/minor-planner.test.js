import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createSSRApp} from 'vue';
import {renderToString} from '@vue/server-renderer';
import {locale} from './i18n.js';
test('minor preview distinguishes catalog mismatch, planned coverage, missing inventory and conditional options',async()=>{
 const path=new URL('./components/MinorPlanner.vue',import.meta.url),generated=new URL(`./components/.minor-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(path,'utf8'));await writeFile(generated,compileScript(descriptor,{id:'minor-test',inlineTemplate:true}).content);
 try{
  const {default:Minor}=await import(generated.href);
  const assessment={inventoryAvailable:true,catalogYear:'2026-27',catalogYearMatches:false,campusMatches:true,reportCatalogYear:'Spring 2023',completed:19,planned:0,additionalCredits:7,groups:[{id:'upper',label:'400-level mathematics',required:12,completed:5,inProgress:0,remaining:7,status:'additional_needed',matchedCourses:[],options:[{code:'MATH 412',title:'Example',sourceUrl:'javascript:alert(1)',creditsMin:3,creditsMax:3,prerequisiteText:'MATH 311W'}]}],reviewNotes:[],sourceUrl:'https://bulletins.psu.edu/undergraduate/',departmentUrl:'https://science.psu.edu/math/'};
  const html=await renderToString(createSSRApp(Minor,{assessment}));for(const text of ['Spring 2023','Six credits unique','MATH 412','MATH 311W','Minimum additional credits'])assert.ok(html.includes(text));assert.ok(!html.includes('href="javascript:'));
  locale.value='ko';const unknown=await renderToString(createSSRApp(Minor,{assessment:{...assessment,inventoryAvailable:false}}));assert.ok(unknown.includes('누락된 과목을 0학점'));assert.ok(!unknown.includes('최소 추가 필요 학점'));
 }finally{locale.value='en';await unlink(generated);}
});
