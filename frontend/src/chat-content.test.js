import test from 'node:test';
import assert from 'node:assert/strict';
import {chatParts,safeWebUrl,searchDocument} from './chat-content.js';
test('web citation tokens link only to supplied safe source IDs',()=>{
 const parts=chatParts('Try this [W2], unknown [W99].',[{id:'W2',url:'https://www.reddit.com/r/PennStateUniversity',title:'Discussion'}]);
 assert.equal(parts.find(p=>p.text==='[W2]').url,'https://www.reddit.com/r/PennStateUniversity');
 assert.equal(parts.find(p=>p.text==='[W99]').url,undefined);
});
test('HTML stays text and dangerous URL protocols cannot become links',()=>{
 for(const url of ['javascript:alert(1)','data:text/html,hi','http://example.com','https://user:secret@example.com'])assert.equal(safeWebUrl(url),null);
 const text='<img src=x onerror=alert(1)> [bad](javascript:alert(1))';
 assert.deepEqual(chatParts(text),[{text}]);
 assert.equal(chatParts('[Bulletin](https://bulletins.psu.edu/)')[0].url,'https://bulletins.psu.edu/');
});
test('search widget document has a restrictive CSP',()=>{
 const doc=searchDocument('<div>Search suggestions</div>');
 assert.ok(doc.includes("default-src 'none'"));assert.ok(doc.includes("form-action 'none'"));
});
