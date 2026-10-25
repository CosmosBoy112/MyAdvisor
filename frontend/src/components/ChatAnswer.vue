<script setup>
import {computed} from 'vue';
import {t} from '../i18n.js';
import {chatParts,safeWebUrl,searchDocument} from '../chat-content.js';
const props=defineProps({text:{type:String,default:''},research:Object});
const sources=computed(()=>(props.research?.sources||[]).filter(s=>safeWebUrl(s.url)));
const parts=computed(()=>chatParts(props.text,sources.value));
</script>
<template>
 <div class="chat-answer">
  <div class="answer-text"><template v-for="(part,i) in parts" :key="i"><a v-if="part.url" :href="part.url" target="_blank" rel="noopener noreferrer">{{part.text}}</a><template v-else>{{part.text}}</template></template></div>
  <div v-if="research" class="web-evidence">
   <p v-if="research.status==='searched'" class="web-caution">{{t('Web findings may be outdated or subjective. Confirm course eligibility and academic decisions with your adviser.')}}</p>
   <p v-else class="web-caution">{{research.status==='no_results'?t('No supported web findings were returned.'):research.status==='quota_exceeded'?t('Web search quota is exhausted. This answer could not be verified online.'):t('Web search is temporarily unavailable. This answer could not be verified online.')}}</p>
   <details v-if="sources.length"><summary>{{t('Web sources')}} · {{sources.length}}</summary><p v-if="research.retrievedAt" class="retrieved">{{t('Retrieved:')}} {{new Date(research.retrievedAt).toLocaleString()}}</p><ul><li v-for="source in sources" :key="source.id"><a :href="safeWebUrl(source.url)" target="_blank" rel="noopener noreferrer">[{{source.id}}] {{source.title}}</a><small>{{source.kind==='official'?t('Official PSU source'):source.kind==='community'?t('Community opinion'):t('Web source — verify publisher')}}</small></li></ul><p>{{t('Retrieval date is not publication date. Reviews do not establish degree requirements.')}}</p></details>
   <iframe v-if="research.searchEntryPoint" :srcdoc="searchDocument(research.searchEntryPoint)" sandbox="allow-popups allow-popups-to-escape-sandbox" referrerpolicy="no-referrer" :title="t('Google Search suggestions')" class="search-suggestions"></iframe>
  </div>
 </div>
</template>
<style scoped>
.chat-answer{min-width:0}.answer-text{white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.75}.chat-answer a{color:var(--psu-blue);text-decoration:underline;text-underline-offset:3px;overflow-wrap:anywhere}.web-evidence{white-space:normal;border-top:1px solid var(--line);margin-top:14px;padding-top:12px;font-size:var(--text-small);line-height:1.65}.web-caution{color:var(--muted);margin:0 0 10px}.web-evidence summary{cursor:pointer;font-weight:600}.web-evidence ul{padding-left:18px}.web-evidence li{margin:12px 0}.web-evidence small{display:block;color:var(--muted)}.retrieved{margin-top:8px;color:var(--muted)}.search-suggestions{display:block;width:100%;height:180px;border:0;margin-top:12px;background:var(--surface);border-radius:8px}
</style>
