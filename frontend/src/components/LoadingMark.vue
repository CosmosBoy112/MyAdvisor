<script setup>
defineProps({size:{type:Number,default:44}});
</script>
<template>
 <svg class="loading-mark" :width="size" :height="size" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
  <circle class="loading-track" cx="24" cy="24" r="19" fill="none" stroke-width="2"/>
  <g class="loading-orbit"><circle cx="24" cy="24" r="19" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="30 90"/><circle cx="24" cy="5" r="2.5" fill="currentColor"/></g>
  <circle cx="21" cy="27" r="7" fill="none" stroke="currentColor" stroke-width="3.5"/>
  <path d="m23 18 7-4 7 4-7 4z" fill="currentColor"/>
  <path d="M26 21v4q4 3 8 0v-4M36 19v6" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/>
 </svg>
</template>
<style scoped>
.loading-mark{display:inline-block;vertical-align:middle;flex-shrink:0;color:var(--psu-blue)}
.loading-track{stroke:var(--line)}
.loading-orbit{transform-origin:24px 24px;animation:loading-orbit 1.8s linear infinite}
@keyframes loading-orbit{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.loading-orbit{animation:none}}
</style>
