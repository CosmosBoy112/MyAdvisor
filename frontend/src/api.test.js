import test from 'node:test';
import assert from 'node:assert/strict';
import {apiRequest} from './api.js';
const never=()=>new Promise(()=>{});
test('stalled token acquisition terminates and never uploads',async()=>{
 let sent=false;
 await assert.rejects(apiRequest('/test',{}, {getHeaders:never,fetchImpl:()=>{sent=true;},authTimeoutMs:10,timeoutMs:100}),/Sign-in verification/);
 assert.equal(sent,false);
});
test('deadline covers a stalled response body, not only response headers',async()=>{
 await assert.rejects(apiRequest('/test',{}, {getHeaders:async()=>({}),fetchImpl:async()=>({status:200,ok:true,text:never}),timeoutMs:15}),/timed out/);
});
test('cancel during authentication releases caller; late token cannot send file',async()=>{
 let resolveHeaders,sent=false;const controller=new AbortController();
 const request=apiRequest('/test',{}, {getHeaders:()=>new Promise(r=>resolveHeaders=r),fetchImpl:()=>{sent=true;},signal:controller.signal});
 controller.abort();await assert.rejects(request,/cancelled/);resolveHeaders({});await new Promise(r=>setTimeout(r,0));assert.equal(sent,false);
});
test('successful response and upstream error stay distinguishable',async()=>{
 const getHeaders=async()=>({Authorization:'Bearer synthetic'});
 assert.deepEqual(await apiRequest('/test',{}, {getHeaders,fetchImpl:async()=>({status:200,ok:true,text:async()=>'{"id":"ok"}'})}),{id:'ok'});
 await assert.rejects(apiRequest('/test',{}, {getHeaders,fetchImpl:async()=>({status:504,ok:false,text:async()=>'{"message":"AI deadline exceeded"}'})}),/AI deadline exceeded/);
});

test('HTML authentication errors retain status-specific guidance',async()=>{
 for(const [status,reason] of [[401,/session could not be verified/],[403,/Access is unavailable/]]){
  await assert.rejects(apiRequest('/api/reports',{}, {getHeaders:async()=>({}),fetchImpl:async()=>new Response('<html>error</html>',{status})}),reason);
 }
});
test('HTML app fallbacks and proxy errors explain the connection problem without displaying raw HTML',async()=>{
 for(const [status,reason] of [[200,/web page instead of data/],[502,/HTTP 502/],[500,/HTTP 500/]]){
  await assert.rejects(apiRequest('/api/reports',{}, {getHeaders:async()=>({}),fetchImpl:async()=>new Response('<html>private diagnostic</html>',{status})}),e=>reason.test(e.message)&&!e.message.includes('private diagnostic'));
 }
});
test('empty success cannot masquerade as a report list; empty deletion is allowed',async()=>{
 const settings={getHeaders:async()=>({}),fetchImpl:async()=>new Response('',{status:200})};
 await assert.rejects(apiRequest('/api/reports',{},settings),/empty response/);
 assert.equal(await apiRequest('/api/reports/id',{method:'DELETE'},settings),null);
 assert.equal(await apiRequest('/api/reports/id',{method:'DELETE'},{...settings,fetchImpl:async()=>new Response(null,{status:204})}),null);
 assert.deepEqual(await apiRequest('/api/reports',{}, {...settings,fetchImpl:async()=>new Response('[]',{status:200})}),[]);
});
