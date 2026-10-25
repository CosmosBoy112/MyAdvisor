<script setup>
import {t} from '../i18n.js';
defineProps({item:{type:Object,required:true}});
</script>
<template>
 <section v-if="item.appliedCoursesVerified || item.status==='complete' || item.reportedStatus==='satisfied'" class="applied-courses">
  <h3>{{t('Courses counted toward this requirement')}}</h3>
  <template v-if="item.appliedCoursesVerified && item.appliedCourses?.length">
   <div class="course-table-scroll"><table><thead><tr><th scope="col">{{t('Course')}}</th><th scope="col">{{t('Term')}}</th><th scope="col">{{t('Credits')}}</th><th scope="col">{{t('Grade / status')}}</th><th scope="col">{{t('PDF page')}}</th></tr></thead><tbody><tr v-for="course in item.appliedCourses" :key="course.code+'-'+course.term"><th scope="row"><strong>{{course.code}}</strong><span v-if="course.title">{{course.title}}</span></th><td>{{course.term}}</td><td>{{course.credits}}</td><td><span :class="{'pending-course':course.status==='in_progress'}">{{course.status==='in_progress'?t('In progress / planned'):course.grade}}</span></td><td>{{course.page}}</td></tr></tbody></table></div>
   <p>{{t('These courses appear in this requirement’s PDF table. IP courses are not earned credits yet.')}}</p>
  </template>
  <p v-else>{{t('No verified course-level table is available for this requirement. GPA and residency requirements may not list individual courses.')}}</p>
 </section>
</template>
<style scoped>
.applied-courses{margin:18px 0;padding:18px;border:1px solid var(--line);border-radius:10px;background:var(--surface)}h3{margin:0 0 12px;color:var(--ink);font-size:var(--text-body);font-weight:600}.course-table-scroll{overflow-x:auto}table{border-collapse:collapse;width:100%;text-align:left;font-size:var(--text-small)}th,td{padding:10px 12px;border-bottom:1px solid var(--line);vertical-align:top}thead th{font-weight:500;white-space:nowrap;color:var(--muted)}tbody th{font-weight:400;min-width:170px}tbody strong{display:block;font-weight:600;color:var(--ink)}tbody th span{display:block;color:var(--muted)}td{white-space:nowrap;font-variant-numeric:tabular-nums}.pending-course{color:var(--psu-blue);font-weight:500}p{font-size:var(--text-small);line-height:1.7;color:var(--muted);margin:12px 0 0}@media(max-width:600px){.applied-courses{padding:12px}th,td{padding:8px}}
</style>
