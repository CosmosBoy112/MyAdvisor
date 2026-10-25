export function safeWebUrl(raw){
 try{const url=new URL(raw);return url.protocol==='https:'&&!url.username&&!url.password?url.href:null;}catch{return null;}
}
export function chatParts(text,sources=[]){
 const links=new Map(sources.filter(s=>/^W\d+$/.test(s.id)&&safeWebUrl(s.url)).map(s=>[s.id,s]));
 const parts=[];const pattern=/\[(W\d+)\]|\[([^\]\n]+)\]\((https:\/\/[^\s)]+)\)/g;let last=0;
 for(const match of String(text||'').matchAll(pattern)){
  if(match.index>last)parts.push({text:text.slice(last,match.index)});
  const source=links.get(match[1]);
  const url=match[1]?source?.url:safeWebUrl(match[3]);
  parts.push(url?{text:match[1]?`[${match[1]}]`:match[2],url:safeWebUrl(url)}:{text:match[0]});last=match.index+match[0].length;
 }
 if(last<text.length)parts.push({text:text.slice(last)});
 return parts;
}
// Light Markdown for model answers: headings, lists, bold, italic and inline code.
// Produces plain data rendered by Vue templates, so model text is never inserted as HTML.
function styled(text){
 const parts=[];const pattern=/\*\*(.+?)\*\*|__(.+?)__|`([^`\n]+)`|(?<![\w*])\*(?!\s)([^*\n]+?)\*(?![\w*])/g;let last=0;
 for(const match of text.matchAll(pattern)){
  if(match.index>last)parts.push({text:text.slice(last,match.index)});
  if(match[1]!=null||match[2]!=null)parts.push({text:match[1]??match[2],bold:true});
  else if(match[3]!=null)parts.push({text:match[3],code:true});
  else parts.push({text:match[4],italic:true});
  last=match.index+match[0].length;
 }
 if(last<text.length)parts.push({text:text.slice(last)});
 return parts;
}
export function inlineParts(text,sources=[]){return chatParts(text,sources).flatMap(part=>part.url?[part]:styled(part.text));}
export function chatBlocks(text,sources=[]){
 const blocks=[];let paragraph=null,list=null;
 const close=()=>{paragraph=null;list=null;};
 for(const raw of String(text||'').replace(/\r\n?/g,'\n').split('\n')){
  const line=raw.trimEnd();let match;
  if(!line.trim()){close();continue;}
  if((match=line.match(/^\s{0,3}(#{1,6})\s+(.*?)\s*#*$/))){close();blocks.push({type:'heading',level:Math.min(match[1].length,3),parts:inlineParts(match[2],sources)});continue;}
  if(/^\s{0,3}([-*_])(\s*\1){2,}\s*$/.test(line)){close();blocks.push({type:'rule'});continue;}
  const bullet=line.match(/^\s*[-*•]\s+(.*)$/),numbered=line.match(/^\s*(\d+)[.)]\s+(.*)$/);
  if(bullet||numbered){
   const ordered=Boolean(numbered);
   if(!list||list.ordered!==ordered){paragraph=null;list={type:'list',ordered,start:ordered?Number(numbered[1]):1,items:[]};blocks.push(list);}
   list.items.push(inlineParts(ordered?numbered[2]:bullet[1],sources));continue;
  }
  if(list&&/^\s{2,}\S/.test(raw)){list.items[list.items.length-1].push({text:' '},...inlineParts(line.trim(),sources));continue;}
  list=null;
  if(!paragraph){paragraph={type:'paragraph',lines:[]};blocks.push(paragraph);}
  paragraph.lines.push(inlineParts(line,sources));
 }
 return blocks;
}
// Provider Search Suggestions are isolated from the application, without scripts or same-origin access.
export function searchDocument(html){return '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\' https:; img-src https: data:; font-src https:; form-action \'none\'">'+html;}
