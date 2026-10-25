<script setup>
defineProps({size:{type:Number,default:44}});
</script>
<template>
 <svg class="loading-mark" :width="size" :height="size" viewBox="0 0 48 48" aria-hidden="true" focusable="false">
  <circle class="loading-track" cx="24" cy="24" r="19" fill="none" stroke-width="2"/>
  <g class="loading-orbit"><circle cx="24" cy="24" r="19" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-dasharray="30 90"/><circle cx="24" cy="5" r="2.5" fill="currentColor"/></g>
  <g transform="translate(10.5 10) scale(.75)">
   <path d="M9 16.5v7.5c4.8 4.2 13.2 4.2 18 0v-7.5L18 21z" fill="currentColor" opacity=".6"/>
   <path d="M1.5 13 18 5l16.5 8L18 21z" fill="currentColor"/>
   <circle class="loading-button" cx="18" cy="13" r="1.6"/>
   <path d="M29.5 16.2V27" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/>
   <path d="M27.8 26.5h3.4l.8 4.5h-5z" fill="currentColor"/>
  </g>
 </svg>
</template>
<style scoped>
.loading-mark{display:inline-block;vertical-align:middle;flex-shrink:0;color:var(--psu-blue)}
.loading-track{stroke:var(--line)}
.loading-button{fill:var(--surface,#fff)}
.loading-orbit{transform-origin:24px 24px;animation:loading-orbit 1.8s linear infinite}
@keyframes loading-orbit{to{transform:rotate(360deg)}}
@media(prefers-reduced-motion:reduce){.loading-orbit{animation:none}}
</style>
