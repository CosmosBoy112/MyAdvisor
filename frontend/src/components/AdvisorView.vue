<script setup>
import {computed} from 'vue';
import {X} from 'lucide-vue-next';
import {t} from '../i18n.js';
import {viewTitle,seasonLabel,reportStatus,prerequisiteRows,planProgress,officialUrl,PREREQ_LABELS} from '../advisor-view.js';
import MinorPlanner from './MinorPlanner.vue';
const props=defineProps({view:{type:Object,required:true},inventory:Object});
defineEmits(['close','ask']);
const data=computed(()=>props.view.data||{});
const plan=computed(()=>props.view.type==='semester_plan'?planProgress(data.value):null);
</script>
<template>
 <section class="advisor-view panel" aria-labelledby="advisor-view-title">
  <div class="advisor-view-heading">
   <div><span class="eyebrow">{{t('Opened by your advisor assistant')}}</span><h2 id="advisor-view-title">{{t(viewTitle(view))}}</h2></div>
   <button type="button" class="icon-button" @click="$emit('close')" :aria-label="t('Close screen')"><X :size="18"/></button>
  </div>

  <p v-if="data.status==='unavailable'" class="view-note">{{data.message}}</p>

  <template v-else-if="view.type==='course_detail'">
   <article v-for="item in data.courses" :key="item.code" class="view-card">
    <template v-if="item.course">
     <div class="view-card-heading"><h3>{{item.code}} · {{item.course.title}}</h3><span class="subtle-pill">{{item.course.credits}} {{t('credits')}}</span></div>
     <p v-if="item.course.description" class="view-description">{{item.course.description}}</p>
     <div v-if="item.prerequisites" class="prereq-block">
      <p class="prereq-status" :class="item.prerequisites.status">{{t(PREREQ_LABELS[item.prerequisites.status]||item.prerequisites.status)}}</p>
      <ul class="prereq-tree"><li v-for="(row,i) in prerequisiteRows(item.prerequisites.tree)" :key="i" :style="{paddingLeft:row.depth*16+'px'}" :class="row.result">
       <template v-if="row.group">{{row.group==='ANY'?t('One of:'):t('All of:')}}</template>
       <template v-else-if="row.course"><strong>{{row.course}}</strong><span v-if="row.minGrade"> ({{row.minGrade}}+)</span> — {{t(PREREQ_LABELS[row.result]||row.result)}}<span v-if="row.grade"> · {{row.grade}}</span></template>
       <template v-else>{{row.text||t('Could not be verified')}}</template>
      </li></ul>
      <p v-if="item.prerequisites.prerequisite_text" class="view-source-text">“{{item.prerequisites.prerequisite_text}}”</p>
     </div>
     <p v-else-if="item.prerequisitesMessage" class="view-note">{{item.prerequisitesMessage}}</p>
     <p v-if="item.course.unlocks?.length" class="view-meta">{{t('Opens:')}} {{item.course.unlocks.join(', ')}}</p>
     <a v-if="officialUrl(item.course.source_url)" :href="officialUrl(item.course.source_url)" target="_blank" rel="noopener noreferrer">{{t('Official Bulletin ↗')}}</a>
    </template>
    <p v-else class="view-note"><strong>{{item.code}}</strong> — {{t('Not in the catalog database')}}</p>
   </article>
  </template>

  <template v-else-if="view.type==='course_options'">
   <table class="view-table"><thead><tr><th>{{t('Course')}}</th><th>{{t('Credits')}}</th><th>{{t('Prerequisites')}}</th><th>{{t('Your report')}}</th></tr></thead>
    <tbody><tr v-for="c in data.courses" :key="c.code">
     <td><a v-if="officialUrl(c.source_url)" :href="officialUrl(c.source_url)" target="_blank" rel="noopener noreferrer"><strong>{{c.code}}</strong></a><strong v-else>{{c.code}}</strong><span v-if="c.title"> · {{c.title}}</span><span v-if="c.status" class="view-meta"> {{t('Not in the catalog database')}}</span></td>
     <td>{{c.credits||'—'}}</td><td :class="c.prerequisiteStatus">{{t(PREREQ_LABELS[c.prerequisiteStatus]||'Could not be verified')}}</td>
     <td :class="reportStatus(c.inReport).key">{{t(reportStatus(c.inReport).label)}}<span v-if="reportStatus(c.inReport).grade"> · {{reportStatus(c.inReport).grade}}</span></td>
    </tr></tbody>
   </table>
  </template>

  <template v-else-if="view.type==='requirement_gap'">
   <p v-if="!data.requirements?.length" class="view-note">{{t('Every requirement in your report is completed or covered by current courses.')}}</p>
   <ul class="gap-list"><li v-for="r in data.requirements" :key="r.title"><strong>{{r.title}}</strong><span v-if="r.required!=null"> · {{r.completed??'—'}} / {{r.required}} {{t('credits')}}<template v-if="r.inProgress"> · {{r.inProgress}} {{t('In progress')}}</template></span><p v-if="r.description">{{r.description}}</p></li></ul>
   <article v-for="o in data.courseOptions" :key="o.node_id" class="view-card"><h3>{{t('Courses that can fill:')}} {{o.label}}</h3>
    <p v-if="o.course_list==='not_listed_in_database'" class="view-note">{{t('The approved course list is not in the database. Ask your adviser.')}}</p>
    <ul class="option-list"><li v-for="c in o.courses" :key="c.code" :class="reportStatus(c.in_report).key"><strong>{{c.code}}</strong><span v-if="c.title"> · {{c.title}}</span><span v-if="c.credits"> · {{c.credits}}</span> — {{t(reportStatus(c.in_report).label)}}</li></ul>
   </article>
  </template>

  <template v-else-if="view.type==='semester_plan'">
   <p class="view-meta">{{data.program}} · {{data.option}} · {{data.totalCredits}} {{t('credits')}} · {{plan.done}} / {{plan.total}} {{t('named courses in your report')}}<template v-if="plan.progress"> · {{plan.progress}} {{t('In progress')}}</template></p>
   <div class="plan-grid"><article v-for="term in data.terms" :key="term.year+term.season" class="plan-term">
    <h3>{{t('Year')}} {{term.year}} · {{t(seasonLabel(term.season))}}<span>{{term.totalCredits}} {{t('credits')}}</span></h3>
    <ul><li v-for="(c,i) in term.courses" :key="i" :class="reportStatus(c.inReport).key"><span>{{c.label}}</span><small>{{c.credits}} · {{t(reportStatus(c.inReport).label)}}<template v-if="reportStatus(c.inReport).grade"> {{reportStatus(c.inReport).grade}}</template></small></li></ul>
   </article></div>
   <a v-if="officialUrl(data.sourceUrl)" :href="officialUrl(data.sourceUrl)" target="_blank" rel="noopener noreferrer">{{t('Official Bulletin ↗')}}</a>
  </template>

  <MinorPlanner v-else-if="view.type==='minor_check'" :assessment="data" :inventory="inventory" @ask="$emit('ask')"/>

  <ul v-if="view.warnings?.length" class="view-warnings"><li v-for="w in view.warnings" :key="w">{{w}}</li></ul>
 </section>
</template>
<style scoped>
.advisor-view{--ok:var(--psu-blue);--bad:#b3261e;--warn:#a88a49;margin:0 0 24px;padding:22px 24px;border:1px solid var(--line);border-radius:14px;background:var(--surface)}
.advisor-view-heading{display:flex;justify-content:space-between;align-items:flex-start;gap:12px;margin-bottom:14px}
.advisor-view-heading h2{margin:4px 0 0;font-size:var(--text-heading);font-weight:600;letter-spacing:-.02em}
.icon-button{border:0;background:transparent;color:var(--muted);cursor:pointer;padding:6px;border-radius:8px}.icon-button:hover{background:var(--line)}
.view-card{border-top:1px solid var(--line);padding:14px 0}.view-card:first-of-type{border-top:0;padding-top:0}
.view-card-heading{display:flex;justify-content:space-between;gap:12px;align-items:baseline}.view-card h3{margin:0 0 8px;font-size:1rem;font-weight:600}
.view-description,.view-meta,.view-note,.view-source-text{color:var(--muted);font-size:var(--text-small);line-height:1.65}
.view-source-text{font-style:italic}
.prereq-status{font-weight:600;margin:10px 0 6px}.prereq-status.met{color:var(--ok)}.prereq-status.not_met{color:var(--bad)}.prereq-status.in_progress,.prereq-status.unknown{color:var(--warn)}
.prereq-tree,.gap-list,.option-list,.view-warnings,.plan-term ul{list-style:none;margin:0;padding:0}
.prereq-tree li{padding:3px 0;font-size:var(--text-small)}.prereq-tree li.met strong{color:var(--ok)}.prereq-tree li.not_met strong{color:var(--bad)}
.view-table{width:100%;border-collapse:collapse;font-size:var(--text-small)}.view-table th,.view-table td{text-align:left;padding:8px 6px;border-bottom:1px solid var(--line);vertical-align:top}
.view-table td.met,.done{color:var(--ok)}.view-table td.not_met{color:var(--bad)}.progress{color:var(--warn)}
.gap-list li{padding:10px 0;border-bottom:1px solid var(--line)}.gap-list p{margin:4px 0 0;color:var(--muted);font-size:var(--text-small)}
.option-list li{padding:4px 0;font-size:var(--text-small)}
.plan-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(220px,1fr));gap:12px;margin:12px 0}
.plan-term{border:1px solid var(--line);border-radius:10px;padding:12px}.plan-term h3{display:flex;justify-content:space-between;margin:0 0 8px;font-size:var(--text-small);font-weight:600}.plan-term h3 span{color:var(--muted);font-weight:400}
.plan-term li{display:flex;flex-direction:column;padding:5px 0;border-top:1px solid var(--line);font-size:var(--text-small)}.plan-term li small{color:var(--muted)}.plan-term li.done span{color:var(--ok)}.plan-term li.progress span{color:var(--warn)}
.view-warnings{margin-top:14px;border-top:1px solid var(--line);padding-top:10px}.view-warnings li{color:var(--muted);font-size:var(--text-small);line-height:1.6;padding:2px 0}
.advisor-view a{color:var(--psu-blue);font-size:var(--text-small)}
:global([data-theme="dark"]) .advisor-view{--bad:#f2b8b5;--warn:#ebcc89}
@media (max-width:640px){.advisor-view{padding:16px}.view-table th:nth-child(2),.view-table td:nth-child(2){display:none}}
</style>
