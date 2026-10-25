<script setup>
import {t} from '../i18n.js';
defineProps({rule:{type:Object,required:true}});
</script>
<template>
 <li class="course-rule">
  <strong>{{rule.label}}</strong>
  <span v-if="rule.type==='GROUP_ALL'"> · {{t('Complete all groups below')}}</span>
  <span v-else-if="rule.type==='GROUP_ANY'"> · {{t('Choose groups:')}} {{rule.min_count??1}}</span>
  <span v-if="rule.min_credits!=null"> · {{rule.min_credits}} {{t('credits minimum')}}</span>
  <span v-if="rule.min_count!=null && rule.type==='POOL'"> · {{t('Choose courses:')}} {{rule.min_count}}</span>
  <span v-if="rule.credits_range?.length===2"> · {{rule.credits_range[0]}}–{{rule.credits_range[1]}} {{t('credits')}}</span>
  <span v-if="rule.min_grade"> · {{t('Minimum grade:')}} {{rule.min_grade}}</span>
  <span v-if="rule.min_gpa!=null"> · GPA ≥ {{rule.min_gpa}}</span>
  <ul v-if="rule.rules?.length"><li v-for="(option,i) in rule.rules" :key="i">{{option.course || (option.attribute ? t('Course attribute:')+' '+option.attribute : t('Selection requires confirmation'))}}</li></ul>
  <p v-if="rule.type==='POOL' && !rule.rules?.length">{{t('Approved course list unavailable — confirm with your adviser.')}}</p>
  <p v-for="note in rule.review||[]" :key="note" class="muted">{{note}}</p>
  <p v-if="rule.requires_review && !rule.review?.length" class="muted">{{t('Needs review')}}</p>
  <ul v-if="rule.children?.length"><CourseRule v-for="(child,i) in rule.children" :key="child.id||i" :rule="child"/></ul>
 </li>
</template>
<style scoped>
.course-rule{margin:10px 0;line-height:1.7}.course-rule>strong{font-weight:600}.course-rule ul{padding-left:20px}.course-rule p{margin:5px 0;font-size:var(--text-small);color:var(--muted)}
</style>
