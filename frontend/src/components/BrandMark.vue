<script setup>
defineProps({size:{type:Number,default:34}});
</script>
<template>
 <svg class="brand-mark" :width="size" :height="size" viewBox="0 0 36 36" aria-hidden="true">
  <path d="M9 16.5v7.5c4.8 4.2 13.2 4.2 18 0v-7.5L18 21z" style="fill:var(--logo-shade)"/>
  <path class="brand-mark-board" d="M1.5 13 18 5l16.5 8L18 21z" style="fill:var(--logo-main)" stroke-width="1.6" stroke-linejoin="round"/>
  <path d="M18 13l11.5 3.2V27" fill="none" style="stroke:var(--logo-accent)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="18" cy="13" r="1.5" style="fill:var(--logo-accent)"/>
  <path d="M27.8 26.5h3.4l.8 4.5h-5z" style="fill:var(--logo-accent)"/>
 </svg>
</template>
<style>
:root{--logo-main:#001e44;--logo-shade:#1e407c;--logo-accent:#96bee6}:root[data-theme="dark"]{--logo-main:#4f78b8;--logo-shade:#3a5f99}
.brand-mark{display:block;overflow:visible}.brand-mark-board{stroke:var(--surface,#fff);paint-order:stroke}
</style>
