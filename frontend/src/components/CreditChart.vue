<script setup>
import {t} from '../i18n.js';
import {computed} from 'vue';
import {progress} from '../model.js';
const props=defineProps({totals:Object});
const chart=computed(()=>progress(props.totals));
const segments=computed(()=>[
 {label:'Completed',value:props.totals?.completed,color:'var(--chart-completed)'},
 {label:props.totals?.includesPlannedTerms?'In progress / planned':'In progress',value:props.totals?.inProgress,color:'var(--chart-progress)'},
 {label:'After current courses',value:chart.value?.additional,color:'var(--chart-remaining)'}
]);
const description=computed(()=>chart.value?`${props.totals.completed} credits completed, ${props.totals.inProgress} in progress, ${chart.value.remaining} still to earn; ${chart.value.additional} additional credits needed after current courses. ${chart.value.percent}% completed.`:'Credit breakdown unavailable: some totals are missing.');
</script>
<template>
 <section class="credit-chart" :aria-label="t(&quot;Credit breakdown&quot;)">
  <div class="chart-heading"><h2>{{t("Credit breakdown")}}</h2></div>
  <div class="chart-body">
   <div class="donut-wrap" role="img" :aria-label="description">
    <svg viewBox="0 0 160 160" aria-hidden="true">
     <circle cx="80" cy="80" r="64" class="ring-track"/>
     <g v-if="chart" transform="rotate(-90 80 80)">
      <circle v-if="chart.completeWidth>0" cx="80" cy="80" r="64" pathLength="100" :stroke-dasharray="`${chart.completeWidth} ${100-chart.completeWidth}`" class="ring-earned"/>
      <circle v-if="chart.progressWidth>0" cx="80" cy="80" r="64" pathLength="100" :stroke-dasharray="`${chart.progressWidth} ${100-chart.progressWidth}`" :stroke-dashoffset="-chart.completeWidth" class="ring-progress"/>
     </g>
    </svg>
    <div class="donut-center" aria-hidden="true"><strong>{{chart?`${chart.percent}%`:'—'}}</strong><span>{{chart?t('completed'):t('Unknown totals')}}</span></div>
   </div>
   <ul class="chart-legend"><li v-for="segment in segments" :key="segment.label"><span class="chart-dot" :style="{background:segment.color}"></span><span>{{t(segment.label)}}</span><strong>{{Number.isFinite(segment.value)?segment.value:'—'}}</strong></li></ul>
  </div>
  <p v-if="totals?.inProgressByTerm" class="term-note"><span v-for="(amount,term) in totals.inProgressByTerm" :key="term">{{term}}: {{amount}} {{t("credits ·")}} </span>{{t("As of the report date.")}}</p>
  <p v-if="!chart">{{t("Upload a report with complete credit totals to see the breakdown.")}}</p>
 </section>
</template>
<style scoped>
.credit-chart{--chart-completed:#1e407c;--chart-progress:#96bee6;--chart-remaining:#dfe7f0;background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:25px;display:flex;flex-direction:column;justify-content:space-between;min-width:0}.chart-heading{display:flex;align-items:center;justify-content:space-between;gap:8px}.chart-heading h2{font-size:var(--text-body);font-weight:600}.chart-heading>span{font-size:var(--text-small);letter-spacing:1.3px;color:var(--muted)}.chart-body{display:flex;flex-wrap:wrap;align-items:center;gap:20px;margin:15px 0}.donut-wrap{position:relative;width:155px;flex-shrink:0}.donut-wrap svg{display:block;width:100%;height:auto}.donut-wrap circle{fill:none;stroke-width:13}.ring-track{stroke:var(--chart-remaining)}.ring-earned{stroke:var(--chart-completed)}.ring-progress{stroke:var(--chart-progress)}.donut-center{position:absolute;inset:0;display:flex;align-items:center;justify-content:center;flex-direction:column;gap:3px}.donut-center strong{font-size:34px;letter-spacing:-1px;font-weight:600;color:var(--ink)}.donut-center span{font-size:var(--text-small);color:var(--muted)}.chart-legend{padding:0;margin:0;list-style:none;flex:1;min-width:130px}.chart-legend li{display:flex;align-items:center;gap:7px;font-size:var(--text-small);color:var(--muted);margin:15px 0;white-space:normal}.chart-dot{width:7px;height:7px;border-radius:50%;flex-shrink:0}.chart-legend strong{margin-left:auto;color:var(--ink);font-size:var(--text-body);font-variant-numeric:tabular-nums}.credit-chart>p{font-size:var(--text-small);line-height:1.7;color:var(--muted)}:global([data-theme="dark"]) .credit-chart{--chart-completed:#729ed6;--chart-progress:#bfd5ee;--chart-remaining:#383e48}@media(min-width:751px) and (max-width:1000px){.chart-body{flex-direction:column;gap:0}.chart-legend{width:100%}.chart-heading>span{display:none}}@media(max-width:400px){.credit-chart{padding:20px}.donut-wrap{width:130px}.chart-body{gap:14px}}
</style>
