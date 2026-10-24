import test from 'node:test';
import assert from 'node:assert/strict';
import {progress,filterRequirements,validateFile} from './model.js';
test('unknown credits stay unknown rather than becoming zero',()=>assert.equal(progress({required:120,completed:null,inProgress:12}),null));
test('in-progress is distinct from earned credits',()=>assert.deepEqual(progress({required:120,completed:72,inProgress:15}),{remaining:33,completeWidth:60,progressWidth:12.5,percent:60}));
test('excess credits never overflow the bar',()=>assert.deepEqual(progress({required:120,completed:125,inProgress:9}),{remaining:0,completeWidth:100,progressWidth:0,percent:100}));
test('review and in-progress requirements remain visible',()=>assert.equal(filterRequirements([{status:'complete'},{status:'review'},{status:'in_progress'}],'remaining').length,2));
test('files are bounded and must be PDF',()=>{assert.ok(validateFile({name:'a.pdf',size:11*1024*1024}));assert.ok(validateFile({name:'a.exe',size:1}));assert.equal(validateFile({name:'a.PDF',size:100}), '');});
