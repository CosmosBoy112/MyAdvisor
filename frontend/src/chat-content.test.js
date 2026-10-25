import test from 'node:test';
import assert from 'node:assert/strict';
import {chatParts,chatBlocks,inlineParts,safeWebUrl,searchDocument} from './chat-content.js';
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
 assert.ok(doc.includes("background:#ffffff!important"));
 assert.ok(doc.includes("color-scheme:light"));
 const dark=searchDocument("<div>Suggestions</div>","dark");
 assert.ok(dark.includes("background:#1b1d20!important"));
 assert.ok(dark.includes("color-scheme:dark"));
});

test('chat answers render headings, lists and emphasis as data, never HTML',()=>{
 const blocks=chatBlocks('### **How to proceed:**\n1.  **Verify with an Adviser:** confirm the *Option*.\n2. Use `CMPSC 465`.\n\n- one\n- two\nPlain <b>text</b> line\nnext line');
 assert.deepEqual(blocks.map(b=>b.type),['heading','list','list','paragraph']);
 assert.equal(blocks[0].level,3);assert.deepEqual(blocks[0].parts,[{text:'How to proceed:',bold:true}]);
 assert.equal(blocks[1].ordered,true);assert.deepEqual(blocks[1].items[0],[{text:'Verify with an Adviser:',bold:true},{text:' confirm the '},{text:'Option',italic:true},{text:'.'}]);
 assert.deepEqual(blocks[1].items[1][1],{text:'CMPSC 465',code:true});
 assert.equal(blocks[2].ordered,false);assert.equal(blocks[2].items.length,2);
 assert.deepEqual(blocks[3].lines.map(l=>l.map(p=>p.text).join('')),['Plain <b>text</b> line','next line']);
 assert.deepEqual(inlineParts('2 * 3 * 4 and snake_case_name'),[{text:'2 * 3 * 4 and snake_case_name'}]);
 const linked=inlineParts('See **[W1]** and [Bulletin](https://bulletins.psu.edu/x)',[{id:'W1',url:'https://example.edu/a'}]);
 assert.ok(linked.some(p=>p.url==='https://bulletins.psu.edu/x'&&p.text==='Bulletin'));assert.ok(linked.some(p=>p.url==='https://example.edu/a'));
});
