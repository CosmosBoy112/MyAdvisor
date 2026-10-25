import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {GlobalRegistrator} from '@happy-dom/global-registrator';

// Mount the actual SFCs with synthetic Auth0 and HTTP boundaries. No real user,
// token, network request, or production authentication bypass is involved.
test('account onboarding, profile reports and conversation-first workspace',async t=>{
 GlobalRegistrator.register({url:'http://localhost:5173'});
 const {parse,compileScript}=await import('@vue/compiler-sfc');
 const {createApp,ref,nextTick}=await import('vue');
 const sample=JSON.parse(await readFile(new URL('./sample.json',import.meta.url),'utf8'));
 const generated=[];const compiled=new Map();
 async function compile(url){
  if(compiled.has(url.href))return compiled.get(url.href);
  const output=new URL(`.account-flow-${process.pid}-${url.pathname.split('/').pop()}.mjs`,url);compiled.set(url.href,output);
  let source=await readFile(url,'utf8');
  source=source.replace("import {useAuth0} from '@auth0/auth0-vue';","const useAuth0=()=>inject('testAuth');");
  source=source.replace("import sample from './sample.json';",`const sample=${JSON.stringify(sample)};`);
  source=source.replace('import.meta.env.VITE_API_BASE_URL',"''");
  for(const match of [...source.matchAll(/from ['"]([^'"]+\.vue)['"]/g)]){
   const child=await compile(new URL(match[1],url));source=source.replace(match[0],`from '${child.href}'`);
  }
  const {descriptor}=parse(source);await writeFile(output,compileScript(descriptor,{id:'account-test',inlineTemplate:true}).content);generated.push(output);return output;
 }
 let app;
 const settle=async()=>{for(let i=0;i<8;i++){await new Promise(r=>setTimeout(r,0));await nextTick();}};
 const button=text=>[...document.querySelectorAll('button')].find(b=>b.textContent.trim()===text);
 const click=async text=>{const b=button(text);assert.ok(b,`button: ${text}`);b.click();await settle();};
 try{
  const {default:App}=await import((await compile(new URL('./App.vue',import.meta.url))).href);
  const {locale}=await import('./i18n.js');
  async function mount(rows,{listFails=false,authenticated=true,hash='#/',clearFails=false}={}){
   app?.unmount();document.body.innerHTML='<div id="test-app"></div>';locale.value='en';sessionStorage.clear();window.history.replaceState(null,'',hash);
   const requests=[];let historyCleared=false;
   const auth={isAuthenticated:ref(authenticated),isLoading:ref(false),error:ref(null),user:ref({sub:'synthetic-user',name:'Test Student',email:'student@example.test'}),getAccessTokenSilently:async()=>'synthetic-test-token',loginWithRedirect:async()=>{},logout:()=>{auth.isAuthenticated.value=false;}};
   globalThis.fetch=async(url,options={})=>{
    requests.push({url,options});
    if(url.endsWith('/health'))return new Response('{"status":"ok","live":true}');
    if(url==='/api/reports'&&!options.method){if(listFails)return new Response('NOT_FOUND',{status:404});return Response.json(rows);}
    if(url.endsWith('/messages')){if(options.method==='DELETE'){if(clearFails)return Response.json({message:'Reset failed'},{status:500});historyCleared=true;return new Response(null,{status:204});}return Response.json(historyCleared?[]:[{role:'assistant',content:'Previous conversation'}]);}
    if(url.endsWith('/chat'))return Response.json({answer:'Synthetic answer',provider:'test'});
    if(url==='/api/reports'&&options.method==='POST'){rows.unshift({id:'new-report',filename:'new-report.pdf',createdAt:'2026-10-24'});return Response.json({...sample,id:'new-report',demo:false,fileName:'new-report.pdf'});}
    if(options.method==='DELETE'){if(rows.length<=1)return Response.json({message:'Keep at least one report.'},{status:409});rows.splice(rows.findIndex(r=>url.endsWith('/'+r.id)),1);return new Response(null,{status:204});}
    return Response.json({...sample,id:url.split('/').pop(),demo:false,fileName:'saved-report.pdf'});
   };
   app=createApp(App);app.provide('authConfigured',true);app.provide('testAuth',auth);app.mount('#test-app');await settle();return {auth,requests};
  }
  await t.test('signed-out introduction opens sample analysis and returns home',async()=>{
   await mount([],{authenticated:false});
   assert.match(document.querySelector('.welcome').textContent,/easy-A courses/);
   assert.match(document.querySelector('.welcome-end').textContent,/Try OnlyPlans/);
   await click('Open the sample analysis');
   assert.ok(document.querySelector('.report-sidebar'));assert.match(document.body.textContent,/Sample data/);
   document.querySelector('.brand').click();await settle();assert.ok(document.querySelector('.welcome'));
  });
  await t.test('sign out appears only beside the profile identity',async()=>{
   await mount([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}]);
   assert.equal(button('Sign out'),undefined);
   await click('Profile');assert.ok(document.querySelector('.profile-identity .profile-signout'));
   assert.equal([...document.querySelectorAll('button')].filter(b=>b.textContent.trim()==='Sign out').length,1);
   await click('Sign out');assert.ok(document.querySelector('.welcome'));
  });
  await t.test('clear chat confirms, preserves the report and stays cleared after reopening',async()=>{
   const {requests}=await mount([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}]);
   const menu=async()=>{document.querySelector('.chat-settings-button').click();await settle();};
   assert.equal(document.querySelector('.clear-chat-button'),null);await menu();assert.ok(document.querySelector('.chat-settings-menu .clear-chat-button'));assert.ok(document.querySelector('.chat-settings-menu .chat-web-toggle'));assert.equal(document.querySelector('.chat-form .clear-chat-button'),null);
   await click('Clear chat');await click('Keep conversation');assert.match(document.querySelector('.chat-body').textContent,/Previous conversation/);
   await menu();await click('Clear chat');document.querySelector('.confirm-actions .danger-button').click();await settle();
   assert.doesNotMatch(document.querySelector('.chat-body').textContent,/Previous conversation/);
   assert.equal(requests.filter(r=>r.options.method==='DELETE').length,1);
   assert.equal(requests.find(r=>r.options.method==='DELETE').url,'/api/reports/saved/messages');
   await click('Profile');assert.equal(document.querySelectorAll('.saved-report').length,1);document.querySelector('.saved-report').click();await settle();document.querySelector('.brand').click();await settle();
   assert.doesNotMatch(document.querySelector('.chat-body').textContent,/Previous conversation/);
   await mount([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}],{clearFails:true});
   await menu();await click('Clear chat');document.querySelector('.confirm-actions .danger-button').click();await settle();
   assert.match(document.querySelector('.chat-body').textContent,/Previous conversation/);assert.match(document.querySelector('[role=dialog]').textContent,/Reset failed/);
  });
  await t.test('first report setup appears after account sign-in, never on the public main page',async()=>{
   const {auth}=await mount([],{authenticated:false});assert.equal(document.querySelector('input[type=file]'),null);assert.equal(button('Add new'),undefined);
   auth.isAuthenticated.value=true;await settle();
   assert.match(document.querySelector('.profile-upload').textContent,/Add your first report/);assert.equal(window.location.hash,'#/profile');document.querySelector('.brand').click();await settle();window.location.hash='#/';await settle();assert.equal(window.location.hash,'#/profile');assert.ok(document.querySelector('main.profile-page'));assert.equal(document.querySelector('[role=dialog]'),null);assert.equal(document.querySelector('.upload-close'),null);assert.ok(document.querySelector('input[type=file]'));assert.equal(document.querySelector('.conversation-main'),null);
   await mount([],{listFails:true});assert.equal(document.querySelector('.profile-upload'),null);assert.match(document.body.textContent,/HTTP 404/);
   await click('Profile');assert.doesNotMatch(document.querySelector('.profile-page').textContent,/No saved reports yet/);
  });
  await t.test('profile URL survives reload and Escape cannot dismiss required setup',async()=>{
   await mount([],{hash:'#/profile'});
   document.querySelector('.app-shell').dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await settle();
   assert.ok(document.querySelector('main.profile-page .profile-upload'));assert.equal(document.querySelector('.conversation-main'),null);
   await mount([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}],{hash:'#/profile'});
   assert.ok(document.querySelector('main.profile-page'));assert.equal(document.querySelector('.conversation-main'),null);
   assert.equal(button('Ask your report'),undefined);document.querySelector('.brand').click();await settle();assert.ok(document.querySelector('.conversation-main'));assert.equal(window.location.hash,'#/');
  });
  await t.test('returning account restores chat; report is optional and profile reopens it',async()=>{
   const {requests}=await mount([{id:'saved-one',filename:'saved-report.pdf',major:'Computer Science',createdAt:'2026-10-24'}]);
   assert.ok(document.querySelector('.conversation-main'));assert.equal(document.querySelector('.report-sidebar'),null);assert.match(document.querySelector('.chat-body').textContent,/Previous conversation/);
   await click('View report');assert.ok(document.querySelector('aside.report-sidebar'));assert.equal(document.querySelector('.report-toggle'),null);document.querySelector('.report-sidebar button[aria-label="Hide report"]').click();await settle();assert.equal(document.querySelector('.report-sidebar'),null);assert.ok(button('View report'));
   await click('Profile');document.querySelector('.saved-report').click();await settle();assert.ok(document.querySelector('.profile-page .profile-report'));assert.equal(document.querySelector('.report-sidebar'),null);assert.equal(document.querySelector('.conversation-main'),null);
   await click('Profile');await click('Add new');assert.ok(document.querySelector('.profile-upload'));assert.equal(document.querySelector('.conversation-main'),null);
   assert.ok(requests.some(r=>r.url==='/api/reports/saved-one/messages'));
  });
  await t.test('chat sidebar lists saved conversations and switches between them',async()=>{
   const {requests}=await mount([{id:'first',filename:'first.pdf',major:'Data Sciences (BS)',createdAt:'2026-10-24',lastQuestion:'Which electives count?'},{id:'second',filename:'second.pdf',major:'Computer Science (BS)',createdAt:'2026-10-23',lastQuestion:null}]);
   const items=()=>[...document.querySelectorAll('.chat-sidebar-item')];
   assert.equal(items().length,2);assert.match(items()[0].textContent,/Which electives count\?/);assert.match(items()[1].textContent,/New conversation/);
   assert.equal(items()[0].getAttribute('aria-current'),'true');
   items()[1].click();await settle();
   assert.ok(requests.some(r=>r.url==='/api/reports/second'));assert.equal(items()[1].getAttribute('aria-current'),'true');assert.ok(document.querySelector('.conversation-main'));
   const box=document.querySelector('#chat-question');box.value='How many credits are left?';box.dispatchEvent(new Event('input',{bubbles:true}));await settle();
   document.querySelector('.chat-form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await settle();
   assert.match(items()[1].textContent,/How many credits are left\?/);
   document.querySelector('.report-toggle').click();await settle();assert.ok(document.querySelector('.report-sidebar'));assert.equal(document.querySelector('.chat-sidebar'),null);
   document.querySelector('.chat-list-toggle').click();await settle();assert.ok(document.querySelector('.chat-sidebar'));assert.ok(document.querySelector('.report-sidebar'));
   document.querySelector('.report-sidebar-heading button').click();await settle();assert.ok(document.querySelector('.chat-sidebar'));
   document.querySelector('.chat-list-toggle').click();await settle();assert.equal(document.querySelector('.chat-sidebar'),null);assert.equal(localStorage.getItem('onlyplans-chat-list'),'hidden');
   document.querySelector('.chat-list-toggle').click();await settle();assert.ok(document.querySelector('.chat-sidebar'));
   await click('Profile');assert.equal(document.querySelector('.chat-sidebar'),null);
   document.querySelector('.saved-report').click();await settle();assert.ok(document.querySelector('.profile-report'));
   document.querySelector('.profile-to-chat').click();await settle();assert.ok(document.querySelector('.conversation-main'));assert.equal(document.querySelector('.profile-report'),null);
  });
  await t.test('profile hides previous report content until loading finishes',async()=>{
   await mount([{id:'saved',filename:'saved.pdf',createdAt:'2026-10-24'}]);
   await click('Profile');document.querySelector('.saved-report').click();await settle();
   assert.ok(document.querySelector('.profile-report'));
   assert.equal(document.querySelector('.saved-report-row'),null);
   assert.equal(document.querySelector('.profile-report-heading'),null);
   await click('My reports');assert.ok(document.querySelector('.saved-report-row'));assert.equal(document.querySelector('.profile-report'),null);
   document.querySelector('.saved-report').click();await settle();
   const originalFetch=globalThis.fetch;let release;
   const pending=new Promise(resolve=>{release=resolve;});
   globalThis.fetch=async(url,options)=>{if(url==='/api/reports')await pending;return originalFetch(url,options);};
   await click('Profile');
   assert.match(document.querySelector('.profile-page .loading-message').textContent,/Loading reports/);assert.ok(document.querySelector('.profile-page .loading-mark'));
   assert.equal(document.querySelector('.saved-report-row'),null);
   assert.equal(document.querySelector('.profile-report'),null);
   assert.equal(document.querySelector('.profile-upload'),null);
   release();await settle();globalThis.fetch=originalFetch;
   assert.equal(document.querySelector('.profile-page .loading-message'),null);
   assert.ok(document.querySelector('.saved-report-row'));
  });
  await t.test('empty account can save one report without entering a question',async()=>{
   const {requests}=await mount([]);
   assert.equal(document.querySelector('.profile-upload textarea'),null);
   const input=document.querySelector('input[type=file]');assert.equal(input.multiple,false);
   Object.defineProperty(input,'files',{value:[new File(['%PDF-test'],'first.pdf',{type:'application/pdf'})],configurable:true});input.dispatchEvent(new Event('change',{bubbles:true}));await settle();
   assert.equal(button('Analyze and save').disabled,true);
   const consent=document.querySelector('.consent input');consent.checked=true;consent.dispatchEvent(new Event('change',{bubbles:true}));await settle();await click('Analyze and save');
   assert.ok(document.querySelector('.conversation-main'));
   const uploads=requests.filter(r=>r.url==='/api/reports'&&r.options.method==='POST');assert.equal(uploads.length,1);assert.equal(uploads[0].options.body.get('file').name,'first.pdf');
   assert.equal(requests.filter(r=>r.url.endsWith('/chat')).length,0);
   await click('Profile');assert.equal(document.querySelectorAll('.saved-report').length,1);assert.match(document.querySelector('.saved-report').textContent,/new-report.pdf/);
   document.querySelector('.saved-report').click();await settle();assert.ok(document.querySelector('.profile-page .profile-report'));assert.equal(document.querySelector('.report-sidebar'),null);
  });
  await t.test('profile disables the last deletion and disables it after deleting a second report',async()=>{
   const {requests}=await mount([{id:'first',filename:'first.pdf',createdAt:'2026-10-24'},{id:'second',filename:'second.pdf',createdAt:'2026-10-23'}]);
   assert.equal(document.querySelector('.profile-upload'),null);await click('Profile');
   document.querySelectorAll('.saved-report-row .saved-report-delete')[1].click();await settle();await click('Delete report');
   assert.equal(document.querySelectorAll('.saved-report').length,1);const last=document.querySelector('.saved-report-row .saved-report-delete');assert.equal(last.disabled,true);last.click();await settle();
   assert.match(document.querySelector('.profile-page').textContent,/Keep at least one report/);assert.equal(requests.filter(r=>r.options.method==='DELETE').length,1);
  });
  await t.test('upload from profile activates main chat and sends the submitted question once',async()=>{
   const {requests}=await mount([{id:'previous',filename:'saved-report.pdf',createdAt:'2026-10-24'}]);
   await click('Profile');await click('Add new');
   const input=document.querySelector('input[type=file]');Object.defineProperty(input,'files',{value:[new File(['%PDF-test'],'test.pdf',{type:'application/pdf'})],configurable:true});input.dispatchEvent(new Event('change',{bubbles:true}));await settle();
   const consent=document.querySelector('.consent input');consent.checked=true;consent.dispatchEvent(new Event('change',{bubbles:true}));
   const goal=document.querySelector('#intent');goal.value='Which courses remain?';goal.dispatchEvent(new Event('input',{bubbles:true}));await settle();
   document.querySelector('.search-card').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));await settle();
   assert.ok(document.querySelector('.conversation-main'));assert.equal(document.querySelector('.report-sidebar'),null);assert.match(document.querySelector('.chat-body').textContent,/Synthetic answer/);
   const calls=requests.filter(r=>r.url.endsWith('/chat'));assert.equal(calls.length,1);assert.equal(JSON.parse(calls[0].options.body).question,'Which courses remain?');
   assert.equal(requests.filter(r=>r.url==='/api/reports'&&r.options.method==='POST').length,1);
   assert.match(document.body.textContent,/Report saved to your profile/);await click('Profile');assert.match(document.querySelector('.profile-page').textContent,/new-report.pdf/);
  });
 }finally{app?.unmount();for(const file of generated)await unlink(file);await GlobalRegistrator.unregister();}
});
