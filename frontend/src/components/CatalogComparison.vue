<script setup>
defineProps({catalog:Object,match:Object});
function sourceUrl(value){try{const u=new URL(value);return u.protocol==='https:'&&['bulletins.psu.edu','www.eecs.psu.edu'].includes(u.hostname)?u.href:null;}catch{return null;}}
</script>
<template>
 <section v-if="catalog" class="catalog-comparison" aria-label="Official catalog comparison">
  <h2>Official catalog comparison</h2>
  <p class="catalog-target">{{catalog.target}}</p>
  <p>{{catalog.message}}</p>
  <p v-if="catalog.status==='matched_with_review'"><strong>{{catalog.matchedRequirements}} report sections matched</strong> · Bulletin minimum: {{catalog.minimumTotalCredits}} credits. Matches need review.</p>
  <p v-if="catalog.totalCreditConflict" class="catalog-conflict">The PDF total differs from the Bulletin minimum. Your PDF totals have been preserved.</p>
  <div class="catalog-links"><a v-if="sourceUrl(catalog.bulletinUrl)" :href="sourceUrl(catalog.bulletinUrl)" target="_blank" rel="noopener noreferrer">Official Bulletin ↗</a><a v-if="sourceUrl(catalog.handbookUrl)" :href="sourceUrl(catalog.handbookUrl)" target="_blank" rel="noopener noreferrer">{{catalog.handbookYear}} Handbook · historical reference ↗</a></div>
 </section>
 <section v-else-if="match" class="catalog-match" aria-label="Requirement catalog match">
  <h3>Bulletin comparison · needs review</h3>
  <p>{{match.message}}</p>
  <p v-if="match.requiredCredits!=null">Current Bulletin requirement: <strong>{{match.requiredCredits}} credits</strong>.</p>
  <p v-if="match.creditConflict" class="catalog-conflict">This differs from the PDF requirement. Confirm the applicable rules with your adviser.</p>
  <details v-if="match.courseOptions?.length"><summary>{{match.courseOptions.length}} course codes listed in the current Bulletin</summary><p>These are reference options, not a personalized eligibility check. Selection rules and prerequisites still apply.</p><ul><li v-for="course in match.courseOptions" :key="course.code"><a v-if="sourceUrl(course.sourceUrl)" :href="sourceUrl(course.sourceUrl)" target="_blank" rel="noopener noreferrer">{{course.code}} ↗</a><span v-else>{{course.code}}</span> — {{course.title||'Title not verified'}}<p v-if="course.prerequisiteText">Prerequisites: {{course.prerequisiteText}}</p><p v-if="course.review?.length">Prerequisite interpretation needs review.</p></li></ul></details>
  <a v-if="sourceUrl(match.bulletinUrl)" :href="sourceUrl(match.bulletinUrl)" target="_blank" rel="noopener noreferrer">View official requirement ↗</a>
 </section>
</template>
<style scoped>
.catalog-comparison,.catalog-match{border:1px solid var(--line);border-radius:12px;background:var(--surface-subtle);padding:20px;margin:20px 0;color:var(--ink);font-size:12px;line-height:1.7}.catalog-comparison h2{font-size:17px;margin:0 0 8px}.catalog-target{font-weight:600}.catalog-comparison p,.catalog-match p{margin:8px 0}.catalog-match h3{font-size:13px;margin:0 0 8px}.catalog-links{display:flex;flex-wrap:wrap;gap:8px 20px;margin-top:12px}a{color:var(--psu-blue);text-underline-offset:3px}.catalog-conflict{font-weight:600}.catalog-match details{margin:10px 0}.catalog-match summary{cursor:pointer}.catalog-match ul{padding-left:20px}.catalog-match li{margin:12px 0}.catalog-match li p{color:var(--muted);font-size:11px}
</style>
