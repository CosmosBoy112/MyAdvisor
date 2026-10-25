<script setup>
import {computed} from 'vue';
import {t} from '../i18n.js';
import {chatBlocks,safeWebUrl,searchDocument} from '../chat-content.js';
const props=defineProps({text:{type:String,default:''},research:Object,theme:{type:String,default:'light'}});
const sources=computed(()=>(props.research?.sources||[]).filter(s=>safeWebUrl(s.url)));
const blocks=computed(()=>chatBlocks(props.text,sources.value));
</script>
<template>
 <div class="chat-answer">
  <div class="answer-text"><template v-for="(block,b) in blocks" :key="b">
   <component :is="'h'+(block.level+2)" v-if="block.type==='heading'" class="answer-heading"><template v-for="(part,i) in block.parts" :key="i"><a v-if="part.url" :href="part.url" target="_blank" rel="noopener noreferrer">{{part.text}}</a><strong v-else-if="part.bold">{{part.text}}</strong><em v-else-if="part.italic">{{part.text}}</em><code v-else-if="part.code">{{part.text}}</code><template v-else>{{part.text}}</template></template></component>
   <hr v-else-if="block.type==='rule'">
   <component :is="block.ordered?'ol':'ul'" v-else-if="block.type==='list'" :start="block.ordered&&block.start!==1?block.start:undefined"><li v-for="(item,n) in block.items" :key="n"><template v-for="(part,i) in item" :key="i"><a v-if="part.url" :href="part.url" target="_blank" rel="noopener noreferrer">{{part.text}}</a><strong v-else-if="part.bold">{{part.text}}</strong><em v-else-if="part.italic">{{part.text}}</em><code v-else-if="part.code">{{part.text}}</code><template v-else>{{part.text}}</template></template></li></component>
   <p v-else><template v-for="(line,l) in block.lines" :key="l"><br v-if="l">
    <template v-for="(part,i) in line" :key="i"><a v-if="part.url" :href="part.url" target="_blank" rel="noopener noreferrer">{{part.text}}</a><strong v-else-if="part.bold">{{part.text}}</strong><em v-else-if="part.italic">{{part.text}}</em><code v-else-if="part.code">{{part.text}}</code><template v-else>{{part.text}}</template></template></template></p>
  </template></div>
  <div v-if="research" class="web-evidence">
   <p v-if="research.status==='searched'" class="web-caution">{{t('Web findings may be outdated or subjective. Confirm course eligibility and academic decisions with your adviser.')}}</p>
   <p v-else class="web-caution">{{research.status==='no_results'?t('No supported web findings were returned.'):research.status==='quota_exceeded'?t('Web search quota is exhausted. This answer could not be verified online.'):t('Web search is temporarily unavailable. This answer could not be verified online.')}}</p>
   <details v-if="sources.length"><summary>{{t('Web sources')}} · {{sources.length}}</summary><p v-if="research.retrievedAt" class="retrieved">{{t('Retrieved:')}} {{new Date(research.retrievedAt).toLocaleString()}}</p><ul><li v-for="source in sources" :key="source.id"><a :href="safeWebUrl(source.url)" target="_blank" rel="noopener noreferrer">[{{source.id}}] {{source.title}}</a><small>{{source.kind==='official'?t('Official PSU source'):source.kind==='community'?t('Community opinion'):t('Web source — verify publisher')}}</small></li></ul><p>{{t('Retrieval date is not publication date. Reviews do not establish degree requirements.')}}</p></details>
   <iframe v-if="research.searchEntryPoint" :srcdoc="searchDocument(research.searchEntryPoint,theme)" sandbox="allow-popups allow-popups-to-escape-sandbox" referrerpolicy="no-referrer" :title="t('Google Search suggestions')" class="search-suggestions"></iframe>
  </div>
 </div>
</template>
<style scoped>
.chat-answer{min-width:0}.answer-text{overflow-wrap:anywhere;line-height:1.75}.answer-text>:first-child{margin-top:0}.answer-text>:last-child{margin-bottom:0}.answer-text p{margin:0 0 12px}.answer-heading{margin:18px 0 8px;font-size:1.05em;font-weight:650;line-height:1.45}h3.answer-heading{font-size:1.15em}.answer-text ul,.answer-text ol{margin:0 0 12px;padding-left:22px}.answer-text li{margin:4px 0;padding-left:2px}.answer-text li::marker{color:var(--muted)}.answer-text strong{font-weight:650}.answer-text code{font-family:ui-monospace,SFMono-Regular,Menlo,monospace;font-size:.9em;padding:1px 6px;border-radius:6px;background:var(--surface-subtle)}.answer-text hr{border:0;border-top:1px solid var(--line);margin:14px 0}.chat-answer a{color:var(--psu-blue);text-decoration:underline;text-underline-offset:3px;overflow-wrap:anywhere}.web-evidence{white-space:normal;border-top:1px solid var(--line);margin-top:14px;padding-top:12px;font-size:var(--text-small);line-height:1.65}.web-caution{color:var(--muted);margin:0 0 10px}.web-evidence summary{cursor:pointer;font-weight:600}.web-evidence ul{padding-left:18px}.web-evidence li{margin:12px 0}.web-evidence small{display:block;color:var(--muted)}.retrieved{margin-top:8px;color:var(--muted)}.search-suggestions{display:block;width:100%;height:180px;border:0;margin-top:12px;background:var(--surface);border-radius:0;box-shadow:none}
</style>
