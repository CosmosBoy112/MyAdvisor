import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {GlobalRegistrator} from '@happy-dom/global-registrator';

// Mounts the real App with synthetic Auth0 and HTTP boundaries (same approach as account-flow.test.js) and checks how
// advisor agent output is shown in the chat: live progress, the screen under the answer, and memory notices.
test('advisor agent progress, screens and memories in the chat workspace',async()=>{
 GlobalRegistrator.register({url:'http://localhost:5173'});
 const {parse,compileScript}=await import('@vue/compiler-sfc');
 const {createApp,ref,nextTick}=await import('vue');
 const sample=JSON.parse(await readFile(new URL('./sample.json',import.meta.url),'utf8'));
 const generated=[];const compiled=new Map();
 async function compile(url){
  if(compiled.has(url.href))return compiled.get(url.href);
  const output=new URL(`.advisor-flow-${process.pid}-${url.pathname.split('/').pop()}.mjs`,url);compiled.set(url.href,output);
  let source=await readFile(url,'utf8');
  source=source.replace("import {useAuth0} from '@auth0/auth0-vue';","const useAuth0=()=>inject('testAuth');");
  source=source.replace("import sample from './sample.json';",`const sample=${JSON.stringify(sample)};`);
  source=source.replace('import.meta.env.VITE_API_BASE_URL',"''");
  for(const match of [...source.matchAll(/from ['"]([^'"]+\.vue)['"]/g)]){const child=await compile(new URL(match[1],url));source=source.replace(match[0],`from '${child.href}'`);}
  const {descriptor}=parse(source);await writeFile(output,compileScript(descriptor,{id:'advisor-flow',inlineTemplate:true}).content);generated.push(output);return output;
 }
 const settle=async()=>{for(let i=0;i<8;i++){await new Promise(r=>setTimeout(r,0));await nextTick();}};
 const wait=ms=>new Promise(r=>setTimeout(r,ms));
 let app;
 try{
  const {default:App}=await import((await compile(new URL('./App.vue',import.meta.url))).href);
  const {locale}=await import('./i18n.js');
  document.body.innerHTML='<div id="test-app"></div>';locale.value='en';window.history.replaceState(null,'','#/');
  const requests=[];let releaseChat;const chatDone=new Promise(r=>{releaseChat=r;});let memories=[{id:7,category:'preference',content:'Avoid 8 am classes'}];
  const view={type:'course_detail',warnings:['Enrollment restrictions were not checked.'],data:{courses:[{code:'CMPSC 465',course:{title:'Data Structures and Algorithms',credits:'3.0'},prerequisites:{status:'met',tree:{type:'COURSE',course:'CMPSC 360',result:'met'}}}]}};
  const auth={isAuthenticated:ref(true),isLoading:ref(false),error:ref(null),user:ref({sub:'synthetic-user',name:'Test Student',email:'student@example.test'}),getAccessTokenSilently:async()=>'synthetic-test-token',loginWithRedirect:async()=>{},logout:()=>{}};
  globalThis.fetch=async(url,options={})=>{
   requests.push({url,options});
   if(url.endsWith('/health'))return new Response('{"status":"ok","live":true}');
   if(url==='/api/reports'&&!options.method)return Response.json([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}]);
   if(url.includes('/chat/progress/'))return Response.json({done:false,steps:[{kind:'llm',detail:'plan'},{kind:'tool',detail:'check_prerequisites'},{kind:'tool',detail:'web_search'}]});
   if(url.endsWith('/chat')){await chatDone;return Response.json({answer:'CMPSC 465 is open to you because CMPSC 360 is done.',provider:'luminal-agent',view,memoryChanges:[{id:8,category:'preference',content:'Prefers morning sections',action:'saved'}]});}
   if(url==='/api/memories')return Response.json(memories);
   if(url.startsWith('/api/memories/')&&options.method==='DELETE'){memories=memories.filter(m=>!url.endsWith('/'+m.id));return new Response(null,{status:204});}
   if(url.endsWith('/messages'))return Response.json([{role:'assistant',content:'Earlier answer',view}]);
   return Response.json({...sample,id:'saved',demo:false,fileName:'saved.pdf'});
  };
  app=createApp(App);app.provide('authConfigured',true);app.provide('testAuth',auth);app.mount('#test-app');await settle();

  // A restored answer keeps its screen behind a chip instead of reopening every screen.
  assert.match(document.querySelector('.chat-body').textContent,/Earlier answer/);
  assert.equal(document.querySelector('.chat-view'),null);
  assert.match(document.querySelector('.view-chip').textContent,/Open screen: Course details/);

  const box=document.getElementById('chat-question');box.value='Can I take CMPSC 465?';box.dispatchEvent(new Event('input'));await settle();
  document.querySelector('.chat-form').dispatchEvent(new Event('submit'));await settle();
  const chat=requests.find(r=>r.url.endsWith('/chat'));const body=JSON.parse(chat.options.body);
  assert.match(body.requestId,/^[0-9a-f-]{36}$/,'the chat request carries a progress id');
  await wait(1300);await settle();
  const progress=document.querySelector('.agent-progress').textContent;
  assert.match(progress,/Checking prerequisites…/);assert.match(progress,/Searching the web…/);
  assert.ok(requests.some(r=>r.url===`/api/reports/saved/chat/progress/${body.requestId}`),'progress is polled for this request');

  releaseChat();await wait(20);await settle();
  assert.equal(document.querySelector('.agent-progress'),null);
  const card=document.querySelector('.chat-view');assert.ok(card,'the new answer opens its screen under the answer');
  assert.match(card.textContent,/Data Structures and Algorithms/);assert.match(card.textContent,/Prerequisites met/);
  assert.match(document.querySelector('.memory-chip').textContent,/Saved to memory: Prefers morning sections/);
  card.querySelector('.icon-button').click();await settle();
  assert.equal(document.querySelector('.chat-view'),null);assert.equal(document.querySelectorAll('.view-chip').length,2,'closing leaves a chip to reopen it');

  [...document.querySelectorAll('.memory-chip button')].find(b=>b.textContent.trim()==='Undo').click();await settle();
  assert.ok(requests.some(r=>r.url==='/api/memories/8'&&r.options.method==='DELETE'));assert.match(document.querySelector('.memory-chip').textContent,/Removed from memory/);

  document.querySelector('.chat-settings-button').click();await settle();
  document.querySelector('.memory-menu-button').click();await settle();
  const modal=document.querySelector('.memory-modal');assert.match(modal.textContent,/Avoid 8 am classes/);
  modal.querySelector('.memory-list button').click();await settle();
  assert.match(document.querySelector('.memory-modal').textContent,/No saved memories yet/);
 }finally{
  app?.unmount();for(const f of generated)await unlink(f).catch(()=>{});await GlobalRegistrator.unregister();
 }
});
