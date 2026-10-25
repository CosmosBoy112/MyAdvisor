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
// Provider Search Suggestions are isolated from the application, without scripts or same-origin access.
export function searchDocument(html){return '<meta http-equiv="Content-Security-Policy" content="default-src \'none\'; style-src \'unsafe-inline\' https:; img-src https: data:; font-src https:; form-action \'none\'">'+html;}
