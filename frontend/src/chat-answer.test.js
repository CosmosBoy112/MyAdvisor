import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createSSRApp} from 'vue';
import {renderToString} from '@vue/server-renderer';
import {locale} from './i18n.js';
test('answer renders cited links, warning, and isolated provider suggestions; quota failures remain explicit',async()=>{
 const source=new URL('./components/ChatAnswer.vue',import.meta.url);
 const generated=new URL(`./components/.chat-answer-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(source,'utf8'));
 await writeFile(generated,compileScript(descriptor,{id:'answer-test',inlineTemplate:true}).content);
 try{
  const {default:Answer}=await import(generated.href);
  const html=await renderToString(createSSRApp(Answer,{text:'A student opinion [W1] <script>alert(1)</script>',research:{status:'searched',sources:[{id:'W1',title:'Student experience',url:'https://www.reddit.com/r/PennStateUniversity/',kind:'community'}],searchEntryPoint:'<div>Google suggestions</div>'}}));
  assert.ok(html.includes('href="https://www.reddit.com/r/PennStateUniversity/"'));assert.ok(html.includes('Community opinion'));assert.ok(html.includes('Confirm course eligibility'));assert.ok(html.includes('&lt;script&gt;'));assert.ok(!html.includes('<script>alert'));assert.ok(html.includes('sandbox="allow-popups allow-popups-to-escape-sandbox"'));
  locale.value='ko';
  const quota=await renderToString(createSSRApp(Answer,{text:'보고서에 따르면…',research:{status:'quota_exceeded'}}));
  assert.ok(quota.includes('사용량 한도를 초과'));assert.ok(!quota.includes('<iframe'));
 }finally{locale.value='en';await unlink(generated);}
});
