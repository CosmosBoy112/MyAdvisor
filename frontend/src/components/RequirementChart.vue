<script setup>
import {computed,ref} from 'vue';
import {progress} from '../model.js';
const props=defineProps({requirements:{type:Array,default:()=>[]}});
const emit=defineEmits(['select']);
const showAll=ref(false);
const rows=computed(()=>props.requirements
 .filter(item=>showAll.value||item.status!=='complete')
 .map(item=>({item,chart:progress(item)}))
 .sort((a,b)=>(b.chart?.remaining??-1)-(a.chart?.remaining??-1)));
function credits(value){return Number.isFinite(value)?new Intl.NumberFormat('en-US',{maximumFractionDigits:2}).format(value):'—';}
function label(row){const {item,chart}=row;return chart?`${item.title}: ${credits(item.completed)} of ${credits(item.required)} credits completed, ${credits(item.inProgress)} in progress, ${credits(chart.remaining)} remaining after current courses.${item.status==='review'?' Needs review.':''}`:`${item.title}: credit totals need confirmation.`;}
</script>
<template>
 <section class="requirement-chart" aria-labelledby="requirement-chart-title">
  <div class="chart-header"><div><h2 id="requirement-chart-title">What’s left, by requirement</h2><p>Largest credit gaps first. Select a row to see its details.</p></div><button class="chart-toggle" type="button" :aria-pressed="showAll" @click="showAll=!showAll">{{showAll?'Hide completed':'Show completed'}}</button></div>
  <div class="chart-key" aria-label="Chart legend"><span><i class="earned"></i>Completed</span><span><i class="current"></i>In progress</span><span><i class="remaining"></i>Remaining</span></div>
  <div v-if="rows.length" class="chart-rows">
   <button v-for="row in rows" :key="row.item.id" type="button" class="chart-row" :aria-label="label(row)+' Open requirement details.'" @click="emit('select',row.item)">
    <span class="row-heading"><span class="row-title">{{row.item.title}}<small v-if="row.item.status==='review'" class="review-label">Needs review</small></span><strong v-if="row.chart?.remaining>0">{{credits(row.chart.remaining)}} <small>credits left</small></strong><strong v-else-if="row.chart && row.item.inProgress>0" class="pending">Pending current courses</strong><strong v-else-if="row.chart && row.item.status==='complete'" class="pending">Complete</strong><strong v-else class="pending">Needs confirmation</strong></span>
    <span v-if="row.chart" class="requirement-track" aria-hidden="true"><span class="earned" :style="{width:row.chart.completeWidth+'%'}"></span><span class="current" :style="{width:row.chart.progressWidth+'%'}"></span></span>
    <span v-else class="requirement-track unknown" aria-hidden="true"></span>
    <span class="row-caption"><span>{{credits(row.item.completed)}} / {{credits(row.item.required)}} credits completed<span v-if="Number.isFinite(row.item.inProgress) && row.item.inProgress>0"> · {{credits(row.item.inProgress)}} in progress</span></span><span>{{row.chart?'View details →':'Credit totals need confirmation →'}}</span></span>
   </button>
  </div>
  <p v-else class="chart-empty">{{requirements.length?'All listed requirements are marked complete. Use “Show completed” to view them.':'No requirements were identified in this report.'}}</p>
  <p class="chart-note">Each bar shows progress toward its own requirement. Remaining assumes current courses are passed. Requirements can overlap; do not add their credits together. Credit progress does not verify course eligibility.</p>
 </section>
</template>
<style scoped>
.requirement-chart{--earned:#1e407c;--current:#96bee6;--remaining:#e3eaf3;margin:28px 0;background:var(--surface);border:1px solid var(--line);border-radius:14px;padding:26px}.chart-header{display:flex;justify-content:space-between;align-items:flex-start;gap:16px}.chart-header h2{font-size:18px;font-weight:550;letter-spacing:-.4px;margin:0}.chart-header p{font-size:12px;color:var(--muted);line-height:1.6;margin:7px 0 0}.chart-toggle{border:1px solid var(--line);border-radius:8px;padding:8px 12px;font-size:11px;white-space:nowrap;background:var(--surface-subtle)}.chart-key{display:flex;gap:20px;flex-wrap:wrap;margin:20px 0 8px;font-size:11px;color:var(--muted)}.chart-key>span{display:flex;align-items:center;gap:7px}.chart-key i{width:9px;height:9px;border-radius:3px}.earned{background:var(--earned)}.current{background:var(--current)}.remaining{background:var(--remaining)}.chart-row{display:block;text-align:left;width:100%;padding:18px 10px;border-radius:8px;color:var(--ink)}.chart-row+.chart-row{border-top:1px solid var(--line)}.chart-row:hover{background:var(--surface-subtle);color:var(--ink)}.row-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:11px}.row-title{font-size:13px;line-height:1.6;display:flex;align-items:center;gap:10px;flex-wrap:wrap}.review-label{font-size:9px;border:1px solid var(--line);color:var(--muted);padding:1px 6px;border-radius:4px}.row-heading>strong{font-size:17px;white-space:nowrap;font-variant-numeric:tabular-nums}.row-heading strong small{font-size:10px;color:var(--muted);font-weight:400}.row-heading>strong.pending{font-size:10px;color:var(--muted);font-weight:500}.requirement-track{display:flex;overflow:hidden;height:10px;border-radius:5px;width:100%;background:var(--remaining)}.requirement-track>span{height:100%}.requirement-track.unknown{background:repeating-linear-gradient(135deg,var(--surface-subtle),var(--surface-subtle) 6px,var(--line) 6px,var(--line) 8px)}.row-caption{display:flex;justify-content:space-between;gap:12px;margin-top:8px;font-size:10px;line-height:1.6;color:var(--muted)}.chart-note,.chart-empty{font-size:10px;line-height:1.8;color:var(--muted);margin:18px 0 0}.chart-empty{font-size:12px;padding:12px 0}:global([data-theme="dark"]) .requirement-chart{--earned:#729ed6;--current:#bfd5ee;--remaining:#383e48}@media(max-width:600px){.requirement-chart{padding:20px 14px}.chart-header{flex-direction:column;gap:10px}.row-heading{align-items:flex-start;gap:10px}.row-title{font-size:12px;gap:4px}.row-heading>strong{font-size:15px}.row-heading strong small{display:block}.row-caption{flex-direction:column;gap:2px}.chart-key{gap:14px}.chart-row{padding:16px 5px}.row-heading>strong.pending{max-width:100px;white-space:normal;text-align:right}}
</style>
