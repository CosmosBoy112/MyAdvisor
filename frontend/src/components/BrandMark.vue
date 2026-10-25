<script setup>
defineProps({size:{type:Number,default:34}});
</script>
<template>
 <svg class="brand-mark" :width="size" :height="size" viewBox="0 0 36 36" aria-hidden="true">
  <circle cx="14" cy="21" r="9.5" fill="none" style="stroke:var(--logo-main)" stroke-width="5.5"/>
  <g class="brand-mark-cap" stroke-linejoin="round">
   <path d="M21.5 13.5v4.2c2.6 1.7 6.4 1.7 9 0v-4.2" style="fill:var(--logo-shade)" stroke-width="2.4"/>
   <path d="M15.5 9.5 26 4l10 5.5-10 5.5z" style="fill:var(--logo-main)" stroke-width="2.4"/>
   <path d="M33.5 10.8v6" fill="none" style="stroke:var(--logo-shade)" stroke-width="1.6" stroke-linecap="round"/>
  </g>
 </svg>
</template>
<style>
:root{--logo-main:#001e44;--logo-shade:#1e407c}:root[data-theme="dark"]{--logo-main:#4f78b8;--logo-shade:#3a5f99}
.brand-mark{display:block;overflow:visible}.brand-mark-cap>path:not(:last-child){stroke:var(--surface,#fff);paint-order:stroke}
</style>
