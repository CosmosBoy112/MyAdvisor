<script setup>
import {t} from '../i18n.js';
import CourseRule from './CourseRule.vue';
defineProps({catalog:Object,match:Object});
function sourceUrl(value){try{const u=new URL(value);return u.protocol==='https:'&&['bulletins.psu.edu','www.eecs.psu.edu'].includes(u.hostname)?u.href:null;}catch{return null;}}
</script>
<template>
 <section v-if="catalog" class="catalog-comparison" :aria-label="t(&quot;Official catalog comparison&quot;)">
  <h2>{{t("Official catalog comparison")}}</h2>
  <p class="catalog-target">{{catalog.target}}</p>
  <p>{{t(catalog.message)}}</p>
  <p v-if="catalog.status==='matched_with_review'"><strong>{{catalog.matchedRequirements}} {{t("report sections matched")}}</strong> {{t("· Bulletin minimum:")}} {{catalog.minimumTotalCredits}} {{t("credits. Matches need review.")}}</p>
  <p v-if="catalog.totalCreditConflict" class="catalog-conflict">{{t("The PDF total differs from the Bulletin minimum. Your PDF totals have been preserved.")}}</p>
  <div class="catalog-links"><a v-if="sourceUrl(catalog.bulletinUrl)" :href="sourceUrl(catalog.bulletinUrl)" target="_blank" rel="noopener noreferrer">{{t("Official Bulletin ↗")}}</a><a v-if="sourceUrl(catalog.handbookUrl)" :href="sourceUrl(catalog.handbookUrl)" target="_blank" rel="noopener noreferrer">{{catalog.handbookYear}} {{t("Handbook · historical reference ↗")}}</a></div>
 </section>
 <section v-else-if="match" class="catalog-match" aria-label="Requirement catalog match">
  <h3>{{t("Bulletin comparison · needs review")}}</h3>
  <p>{{t(match.message)}}</p>
  <p v-if="match.requiredCredits!=null">{{t("Current Bulletin requirement:")}} <strong>{{match.requiredCredits}} {{t("credits")}}</strong>.</p>
  <p v-if="match.creditConflict" class="catalog-conflict">{{t("This differs from the PDF requirement. Confirm the applicable rules with your adviser.")}}</p>
  <div v-if="match.rule" class="selection-rules"><h3>{{t("How to satisfy this requirement")}}</h3><ul><CourseRule :rule="match.rule"/></ul></div><div v-if="match.courseOptions?.length" class="course-options"><h3>{{t("Bulletin course options")}} · {{match.courseOptions.length}}</h3><p>{{t("These are reference options, not a personalized eligibility check. Selection rules and prerequisites still apply.")}}</p><ul><li v-for="course in match.courseOptions" :key="course.code"><a v-if="sourceUrl(course.sourceUrl)" :href="sourceUrl(course.sourceUrl)" target="_blank" rel="noopener noreferrer">{{course.code}} ↗</a><span v-else>{{course.code}}</span> — {{course.title||t('Title not verified')}}<p v-if="course.prerequisiteText">{{t("Prerequisites:")}} {{course.prerequisiteText}}</p><p v-if="course.review?.length">{{t("Prerequisite interpretation needs review.")}}</p></li></ul></div>
  <a v-if="sourceUrl(match.bulletinUrl)" :href="sourceUrl(match.bulletinUrl)" target="_blank" rel="noopener noreferrer">{{t("View official requirement ↗")}}</a>
 </section>
</template>
<style scoped>
.catalog-comparison,.catalog-match{border:1px solid var(--line);border-radius:12px;background:var(--surface-subtle);padding:20px;margin:20px 0;color:var(--ink);font-size:var(--text-small);line-height:1.7}.catalog-comparison h2{font-size:var(--text-heading);margin:0 0 8px}.catalog-target{font-weight:600}.catalog-comparison p,.catalog-match p{margin:8px 0}.catalog-match h3{font-size:var(--text-small);margin:0 0 8px}.catalog-links{display:flex;flex-wrap:wrap;gap:8px 20px;margin-top:12px}a{color:var(--psu-blue);text-underline-offset:3px}.catalog-conflict{font-weight:600}.course-options,.selection-rules{margin:18px 0;padding-top:14px;border-top:1px solid var(--line)}.catalog-match .course-options h3,.catalog-match .selection-rules h3{font-size:var(--text-body)}.catalog-match summary{cursor:pointer}.catalog-match ul{padding-left:20px}.catalog-match li{margin:12px 0}.catalog-match li p{color:var(--muted);font-size:var(--text-small)}
</style>
