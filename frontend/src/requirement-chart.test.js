import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,unlink} from 'node:fs/promises';
import {parse,compileScript} from '@vue/compiler-sfc';
import {createRenderer,nextTick} from 'vue';
import {locale} from './i18n.js';

// Mount the actual SFC with a small in-memory renderer, so event handlers and
// reactive updates are tested without a browser or an additional DOM package.
const node=(tag,text='')=>({tag,text,children:[],props:{},parent:null});
const renderer=createRenderer({
 createElement:tag=>node(tag),createText:text=>node('#text',text),createComment:text=>node('#comment',text),
 setText:(n,text)=>{n.text=text;},setElementText:(n,text)=>{n.text=text;n.children=[];},
 patchProp:(n,key,old,value)=>{n.props[key]=value;},parentNode:n=>n.parent,
 nextSibling:n=>n.parent?.children[n.parent.children.indexOf(n)+1]??null,
 insert(n,parent,anchor=null){if(n.parent){n.parent.children.splice(n.parent.children.indexOf(n),1);}n.parent=parent;const i=anchor?parent.children.indexOf(anchor):-1;if(i<0)parent.children.push(n);else parent.children.splice(i,0,n);},
 remove(n){if(n.parent)n.parent.children.splice(n.parent.children.indexOf(n),1);n.parent=null;}
});
function all(n){return [n,...n.children.flatMap(all)];}
function text(n){return n.text+n.children.map(text).join('');}
const items=[
 {id:'done',title:'Completed course group',status:'complete',required:6,completed:6,inProgress:0},
 {id:'pending',title:'Pending course group',status:'in_progress',required:6,completed:3,inProgress:3},
 {id:'review',title:'Unverified course group',status:'review',required:6,completed:6,inProgress:0}
];
test('chart buttons switch completed rows and explain a zero-result view',async()=>{
 const source=new URL('./components/RequirementChart.vue',import.meta.url);
 const generated=new URL(`./components/.requirement-chart-${process.pid}.mjs`,import.meta.url);
 const {descriptor}=parse(await readFile(source,'utf8'));
 await writeFile(generated,compileScript(descriptor,{id:'chart-test',inlineTemplate:true}).content);
 try{
  const {default:Chart}=await import(generated.href);
  for(const requirements of [items,items.slice(1)]){
   const root=node('root');const app=renderer.createApp(Chart,{requirements});app.mount(root);
   const buttons=()=>all(root).filter(n=>n.tag==='button'&&n.props.class?.includes('chart-toggle'));
   const rows=()=>all(root).filter(n=>n.tag==='button'&&n.props.class==='chart-row');
   assert.equal(rows().length,2);
   const pending=rows().find(n=>text(n).includes('Pending course group'));
   assert.match(text(pending),/3 credits still to earn/);
   assert.match(text(pending),/0 needed after current courses/);
   buttons()[1].props.onClick();await nextTick();
   assert.equal(buttons()[1].props['aria-pressed'],true);
   assert.equal(rows().length,requirements===items?1:0);
   if(requirements===items)assert.match(text(rows()[0]),/Completed course group/);
   else assert.match(text(root),/No requirements are marked complete/);
   buttons()[0].props.onClick();await nextTick();
   assert.equal(rows().length,2);assert.equal(buttons()[0].props['aria-pressed'],true);
   locale.value='ko';await nextTick();
   assert.match(text(root),/요건별 남은 학점/);assert.match(text(buttons()[1]),/완료/);
   assert.match(text(root),/Pending course group/); // Official source title stays original.
   locale.value='en';await nextTick();app.unmount();
  }
 }finally{await unlink(generated);}
});
