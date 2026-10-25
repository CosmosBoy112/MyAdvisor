import test from 'node:test';
import assert from 'node:assert/strict';
import {progress,coveredByPlan,filterRequirements,validateFile} from './model.js';
test('unknown credits stay unknown rather than becoming zero',()=>assert.equal(progress({required:120,completed:null,inProgress:12}),null));
test('in-progress is distinct from earned credits',()=>assert.deepEqual(progress({required:120,completed:72,inProgress:15}),{remaining:48,additional:33,completeWidth:60,progressWidth:12.5,percent:60}));
test('excess credits never overflow the bar',()=>assert.deepEqual(progress({required:120,completed:125,inProgress:9}),{remaining:0,additional:0,completeWidth:100,progressWidth:0,percent:100}));
test('review and in-progress requirements remain visible',()=>assert.equal(filterRequirements([{status:'complete'},{status:'review'},{status:'in_progress'}],'remaining').length,2));
test('files are bounded and must be PDF',()=>{assert.ok(validateFile({name:'a.pdf',size:11*1024*1024}));assert.ok(validateFile({name:'a.exe',size:1}));assert.equal(validateFile({name:'a.PDF',size:100}), '');});

test('current courses do not count as earned even when they cover the entire gap',()=>{
 const result=progress({required:6,completed:3,inProgress:3});
 assert.equal(result.remaining,3);assert.equal(result.additional,0);
 assert.equal(result.completeWidth,50);assert.equal(result.progressWidth,50);
});
test('unknown in-progress credits do not silently become zero',()=>assert.equal(progress({required:6,completed:3,inProgress:null}),null));
test('fractional credits and excess current enrollment remain bounded',()=>{
 const result=progress({required:6,completed:4.5,inProgress:3});
 assert.equal(result.remaining,1.5);assert.equal(result.additional,0);
 assert.equal(result.completeWidth+result.progressWidth,100);
});

test('planned coverage removes only fully covered non-review requirements from remaining',()=>{
 const pending={status:'in_progress',required:6,completed:3,inProgress:3};
 assert.equal(coveredByPlan(pending),true);
 for(const item of [{...pending,inProgress:2},{...pending,status:'review'},{...pending,completed:null},{...pending,status:'remaining'}])assert.equal(coveredByPlan(item),false);
 assert.equal(filterRequirements([pending],'complete').length,1);assert.equal(filterRequirements([pending],'remaining').length,0);assert.equal(pending.status,'in_progress');
});
