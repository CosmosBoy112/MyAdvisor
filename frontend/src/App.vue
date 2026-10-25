<script setup>
import {t as translate,locale,useInputLanguage,pageText,displayLanguage} from './i18n.js';
import {computed,inject,nextTick,ref,watch,onMounted,onUnmounted} from 'vue';
import {useAuth0} from '@auth0/auth0-vue';
import {ArrowUp,ArrowUpRight,ArrowRight,Plus,X,FileText,Check,ChevronDown,ChevronRight,BookOpen,GraduationCap,MessageCircle,ShieldCheck,LoaderCircle,LogOut,Download,Info,Search,RotateCcw,Trash2,Sun,Moon} from 'lucide-vue-next';
import sample from './sample.json';
import CatalogComparison from './components/CatalogComparison.vue';
import CreditChart from './components/CreditChart.vue';
import ChatAnswer from './components/ChatAnswer.vue';
import AppliedCourses from './components/AppliedCourses.vue';
import MinorPlanner from './components/MinorPlanner.vue';
import RequirementChart from './components/RequirementChart.vue';
import {apiRequest} from './api.js';
import {sessionStatus} from './auth-config.js';
import {progress,coveredByPlan,filterRequirements,validateFile} from './model.js';
const theme=ref(document.documentElement.dataset.theme||'light');
function toggleTheme(){
 theme.value=theme.value==='dark'?'light':'dark';
 document.documentElement.dataset.theme=theme.value;
 document.querySelector('meta[name="theme-color"]')?.setAttribute('content',theme.value==='dark'?'#111214':'#ffffff');
 try{localStorage.setItem('lionpathway-theme',theme.value);}catch{}
}
const configured=inject('authConfigured');
const auth=configured?useAuth0():null;
const report=ref(null),intent=ref(''),file=ref(null),fileInput=ref(null),busy=ref(false),error=ref(''),notice=ref(''),dragging=ref(false),filter=ref('all'),expanded=ref(null),source=ref(null),chatOpen=ref(false),question=ref(''),messages=ref([]),chatBusy=ref(false),chatBody=ref(null),consent=ref(false);
const t=text=>pageText(text,Boolean(report.value));
watch([locale,report],()=>{document.documentElement.lang=displayLanguage(Boolean(report.value));},{immediate:true});
const webSearch=ref(true);
const base=import.meta.env.VITE_API_BASE_URL||'';
const uploadStage=ref('auth'),elapsed=ref(0);let uploadController=null;
function cancelUpload(){uploadController?.abort();}
function submitOnEnter(event){
 if(event.key!=='Enter'||event.shiftKey||event.isComposing||event.keyCode===229)return;
 event.preventDefault();
 if(!event.repeat)event.currentTarget.form?.requestSubmit();
}
const deletionNotice=ref('');let deletionNoticeTimer;
function dismissDeletionNotice(){clearTimeout(deletionNoticeTimer);deletionNotice.value='';}
function showDeletionNotice(){
 dismissDeletionNotice();deletionNotice.value='Report and conversation deleted.';
 deletionNoticeTimer=setTimeout(dismissDeletionNotice,3000);
}
onUnmounted(dismissDeletionNotice);
const deleteTarget=ref(null);
const savedReports=ref([]),historyBusy=ref(false),historyOpen=ref(false),reportBusy=ref(false),deleteConfirm=ref(false),deleteBusy=ref(false),service=ref(null),serviceError=ref('');
const signedIn=computed(()=>Boolean(auth?.isAuthenticated.value));
const authLoading=computed(()=>Boolean(auth?.isLoading.value));
const session=computed(()=>sessionStatus(configured,authLoading.value,signedIn.value));
async function checkService(){try{const r=await fetch(base+'/api/health');if(!r.ok)throw Error();service.value=await r.json();serviceError.value='';}catch{serviceError.value='The analysis server is not connected. Start the Spring Boot backend, then retry.';}}
onMounted(()=>{checkService();const draft=sessionStorage.getItem('lionpathway-goal');if(draft){intent.value=draft;sessionStorage.removeItem('lionpathway-goal');notice.value='You’re signed in. Attach your PDF again to begin the analysis.';}});
watch(session,async state=>{if(state==='signed-in')await loadHistory();else if(state==='signed-out')savedReports.value=[];},{immediate:true});
watch(()=>auth?.error.value,e=>{if(e)error.value='Sign-in could not finish: '+(e.message||'Check Auth0 allowed URLs and API audience.');},{immediate:true});
async function loadHistory(){historyBusy.value=true;try{savedReports.value=await request('/api/reports');}catch(e){error.value=e.message;}finally{historyBusy.value=false;}}
async function openReport(id){reportBusy.value=true;error.value='';try{const next=await request(`/api/reports/${id}`);const history=await request(`/api/reports/${id}/messages`);report.value=next;messages.value=history.map(m=>({role:m.role,text:m.content,webResearch:m.webResearch}));historyOpen.value=false;filter.value='all';expanded.value=null;source.value=null;chatOpen.value=false;window.scrollTo(0,0);}catch(e){error.value=e.message;}finally{reportBusy.value=false;}}
function confirmDelete(item){if(deleteBusy.value||item.demo)return;deleteTarget.value={id:item.id,name:item.fileName||item.filename||item.major||'Academic report'};deleteConfirm.value=true;}
async function deleteReport(){
 if(!deleteTarget.value||deleteBusy.value)return;
 const id=deleteTarget.value.id;deleteBusy.value=true;error.value='';
 try{
  await request(`/api/reports/${id}`,{method:'DELETE'});
  savedReports.value=savedReports.value.filter(item=>item.id!==id);
  if(report.value?.id===id)reset();
  deleteConfirm.value=false;deleteTarget.value=null;
  showDeletionNotice();
 }catch(e){error.value=e.message;}finally{deleteBusy.value=false;}
}

const totals=computed(()=>progress(report.value?.totals));
const visible=computed(()=>filterRequirements(report.value?.requirements||[],filter.value));
const completedCount=computed(()=>report.value?.requirements.filter(coveredByPlan).length||0);
const statuses={complete:'Complete',in_progress:'In progress',remaining:'Remaining',review:'Needs review'};
async function headers(){if(!configured||!auth.isAuthenticated.value)throw Error('Sign in to analyze your personal report.');return {Authorization:`Bearer ${await auth.getAccessTokenSilently()}`};}
function request(path,options={},settings={}){return apiRequest(base+path,options,{getHeaders:headers,...settings});}
function pick(f){error.value='';const problem=validateFile(f);if(problem){error.value=problem;return;}file.value=f;}
function drop(event){dragging.value=false;if(event.dataTransfer.files.length)pick(event.dataTransfer.files[0]);}
async function signIn(){error.value='';if(!configured){notice.value='Live sign-in needs Auth0 configuration. You can explore the sample without an account.';return;}try{if(intent.value.trim())sessionStorage.setItem('lionpathway-goal',intent.value);await auth.loginWithRedirect();}catch(e){error.value=e.message;}}
function signOut(){auth?.logout({logoutParams:{returnTo:window.location.origin}});}
function demo(){report.value=structuredClone(sample);messages.value=[];filter.value='all';error.value='';notice.value='';window.scrollTo(0,0);void answerInitialQuestion(intent.value);}
function reset(){report.value=null;messages.value=[];chatOpen.value=false;source.value=null;error.value='';expanded.value=null;deleteConfirm.value=false;}
async function analyze(){const submittedIntent=intent.value.trim();let analysisSucceeded=false;useInputLanguage(submittedIntent);error.value='';if(authLoading.value){error.value='Sign-in is still loading. Please try again in a moment.';return;}if(!file.value){fileInput.value.click();return;}if(!configured){error.value='Live PDF analysis requires Auth0, Gemini, and the backend. Use “Explore a sample” to preview the experience.';return;}if(!auth.isAuthenticated.value){await signIn();return;}if(!consent.value){error.value='Please allow your report text to be sent to the AI providers for analysis.';return;}if(busy.value)return;busy.value=true;elapsed.value=0;uploadStage.value='auth';uploadController=new AbortController();const started=Date.now();const ticker=setInterval(()=>elapsed.value=Math.floor((Date.now()-started)/1000),1000);try{const form=new FormData();form.append('file',file.value);form.append('intent',submittedIntent);form.append('language',locale.value);report.value=await request('/api/reports',{method:'POST',body:form},{signal:uploadController.signal,onStage:stage=>uploadStage.value=stage});messages.value=[];filter.value='all';expanded.value=null;file.value=null;consent.value=false;void loadHistory();window.scrollTo(0,0);analysisSucceeded=true;}catch(e){error.value=e.message;}finally{clearInterval(ticker);uploadController=null;busy.value=false;}if(analysisSucceeded)await answerInitialQuestion(submittedIntent);}
async function answerInitialQuestion(text){
 if(!text.trim())return;
 chatOpen.value=true;question.value=text.trim();
 await send();
}
async function openRequirement(item){
 filter.value='all';expanded.value=item.id;
 await nextTick();
 const target=document.getElementById(`requirement-${item.id}`);
 target?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'center'});
 target?.querySelector('.requirement-summary')?.focus({preventScroll:true});
}
function askAbout(item){chatOpen.value=true;question.value=translate('What does “{title}” mean, and what is left to complete?').replace('{title}',item.title);}
function askCourses(item){chatOpen.value=true;question.value=translate('Which courses can satisfy “{title}”? Explain course options, prerequisites, remaining credits and any uncertain approvals using my report and catalog.').replace('{title}',item.title);}
function askMathMinor(){chatOpen.value=true;question.value=translate('Based on my completed and planned courses, what would I need for the Mathematics minor? Explain additional credits, course options, prerequisites and overlap with my major.');}
function demoAnswer(q){if(locale.value!=='en')return translate('This sample has 72 completed credits, 15 in progress and 33 additional credits needed out of 120. Meeting the credit total does not satisfy every requirement. This is a sample answer, not a live AI response.');let item=report.value.requirements.find(r=>q.toLowerCase().includes(r.title.toLowerCase()));if(!item&&/minor|math/i.test(q))item=report.value.requirements.find(r=>r.id==='minor');if(item)return `${item.description}\n\nSample source: page ${item.evidence.page}. This is an illustrative answer, not a live Gemini response.`;if(/credit|left|remaining/i.test(q))return 'In this sample, 72 of 120 credits are completed, 15 are in progress, and 33 remain beyond current courses. Finishing the credit total does not automatically satisfy every requirement.\n\nThis is an illustrative answer, not a live Gemini response.';return 'This is sample mode. Try asking about Department List, Supportive Electives, the Mathematics minor, or remaining credits. Live Gemini answers become available when the services are configured.';}
async function send(){const q=question.value.trim();if(!q||chatBusy.value)return;useInputLanguage(q);question.value='';messages.value.push({role:'user',text:q});chatBusy.value=true;try{const result=report.value.demo?{answer:demoAnswer(q)}:await request(`/api/reports/${report.value.id}/chat`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:q,language:locale.value,webSearch:webSearch.value})},{timeoutMs:160000});messages.value.push({role:'assistant',text:result.answer,webResearch:result.webResearch});}catch(e){messages.value.push({role:'error',text:e.message});}finally{chatBusy.value=false;await nextTick();chatBody.value?.scrollTo({top:chatBody.value.scrollHeight,behavior:'smooth'});}}
function download(){const blob=new Blob([JSON.stringify(report.value,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='lionpathway-analysis.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function value(n){return Number.isFinite(n)?n:'—';}
</script>

<template>
 <div class="app-shell" :class="{'chat-visible':chatOpen && report}" @keydown.esc="source=null;chatOpen=false;historyOpen=false;deleteConfirm=false">
  <header class="topbar">
   <button class="brand" @click="reset" aria-label="LionPathway home"><span class="brand-icon"><GraduationCap :size="23"/></span><span>Lion<span class="brand-light">Pathway</span><span class="brand-dot">.</span></span></button>
   <div class="header-right"><button type="button" class="theme-toggle" :aria-label="theme==='dark'?'Switch to light mode':'Switch to dark mode'" :title="theme==='dark'?'Switch to light mode':'Switch to dark mode'" @click="toggleTheme"><Sun v-if="theme==='dark'" :size="18"/><Moon v-else :size="18"/></button><span v-if="session==='restoring'" class="session-restoring" role="status"><LoaderCircle :size="14" class="spin"/> {{t("Checking session…")}}</span><button v-if="session==='signed-in'" class="sign-in" @click="historyOpen=true;loadHistory()"><BookOpen :size="15"/> {{t("My reports")}}</button><button v-if="session==='signed-out'" class="sign-in" @click="signIn">{{t("Sign in")}} <ArrowUpRight :size="15"/></button><button v-else-if="session==='signed-in'" class="sign-in" @click="signOut()">{{t("Sign out")}} <LogOut :size="15"/></button></div>
  </header>
  <div v-if="deletionNotice" class="success-toast" role="status" aria-live="polite" aria-atomic="true"><Check :size="18"/><span>{{t(deletionNotice)}}</span><button type="button" @click="dismissDeletionNotice" aria-label="Dismiss deletion notification"><X :size="16"/></button></div>
  <div v-if="notice" class="notice" role="status">{{t(notice)}}<button @click="notice=''" :aria-label="t(&quot;Dismiss&quot;)"><X :size="16"/></button></div>
  <div v-if="error && (report || historyOpen)" class="global-error error" role="alert">{{t(error)}}<button @click="error=''" :aria-label="t(&quot;Dismiss error&quot;)"><X :size="16"/></button></div>
  <main v-if="!report" class="landing">
   <div class="hero-decoration" aria-hidden="true"><span></span><span></span><span></span></div>
   <section class="hero">
    <h1>{{t("Your degree overview")}}</h1>
    <p class="hero-description">{{t("Upload your LionPATH What-If Report.")}}</p>
    <form class="search-card" :class="{dragging}" @submit.prevent="analyze" @dragover.prevent="dragging=true" @dragleave.prevent="dragging=false" @drop.prevent="drop">
     <label class="sr-only" for="intent">{{t("Your major and the minor you are considering")}}</label>
     <textarea id="intent" @keydown="submitOnEnter" :title="t(&quot;Enter to analyze · Shift+Enter for a new line&quot;)" v-model="intent" maxlength="1000" :placeholder="t(&quot;I’m majoring in Computer Science, considering a Math minor…&quot;)" :disabled="busy" rows="2"></textarea>
     <div v-if="file" class="file-chip"><FileText :size="15"/><span>{{file.name}}</span><button type="button" @click="file=null" :aria-label="t(&quot;Remove PDF&quot;)"><X :size="14"/></button></div>
     <div class="search-toolbar"><button type="button" class="attach-button" @click="fileInput.click()" :disabled="busy"><Plus :size="18"/> {{t("Attach report")}} <span>PDF</span></button><button type="submit" class="submit-button" :disabled="busy" :aria-label="t(&quot;Analyze report&quot;)"><LoaderCircle v-if="busy" class="spin" :size="21"/><ArrowUp v-else :size="22"/></button></div>
     <input ref="fileInput" type="file" accept=".pdf,application/pdf" class="sr-only" @change="e=>{if(e.target.files[0])pick(e.target.files[0]);e.target.value=''}">
    </form>
    <label v-if="file" class="consent"><input type="checkbox" v-model="consent"> {{t("Allow report text to be sent to Gemini (and Luminal for planning questions) for analysis. Remove personal identifiers before uploading.")}}</label>
    <div v-if="file && (serviceError || (service && !service.live))" class="service-status"><Info :size="15"/><span>{{serviceError||'The server is in sample-only mode. Start it with the live profile to analyze PDFs.'}}</span><button type="button" @click="checkService">{{t("Retry")}}</button></div>
    <div v-if="busy" class="upload-progress"><p class="loading-message" role="status">{{uploadStage==='auth'?t('Verifying your sign-in…'):t('Uploading and analyzing your report…')}} {{elapsed}}s</p><p v-if="uploadStage==='analysis'" class="upload-hint">{{t("AI analysis can take up to 100 seconds. You can stop waiting at any time.")}}</p><button type="button" class="cancel-upload" @click="cancelUpload">{{t("Stop waiting")}}</button></div>
    <p v-if="error" class="error" role="alert">{{t(error)}}</p>
    <div class="hero-actions"><button @click="demo">{{t("Explore a sample")}} <ArrowRight :size="14"/></button></div>
   </section>
  </main>
  <main v-else class="dashboard">
   <div class="breadcrumb"><button @click="reset">{{t("Home")}}</button><ChevronRight :size="13"/><span>{{t("Your degree overview")}}</span><span v-if="report.demo" class="sample-badge">{{t("SAMPLE REPORT")}}</span></div>
   <div class="page-heading"><div><h1>{{t("Your degree overview")}}</h1><p>{{report.major||t('Major needs confirmation')}} <span>·</span> {{report.campus||t('Campus not specified')}}</p></div><div class="report-heading-actions"><button v-if="!report.demo" class="outline-button delete-link" :disabled="deleteBusy" @click="confirmDelete(report)"><Trash2 :size="16"/> {{t("Delete report")}}</button><button class="outline-button" @click="reset"><Plus :size="16"/> {{t("New report")}}</button></div></div>
   <div v-if="report.demo" class="sample-notice"><Info :size="17"/><span>{{t("Sample data · no live AI analysis.")}}</span></div>
   <section class="overview-grid">
    <div class="progress-panel panel"><div class="panel-heading"><span>{{t("Degree progress")}}</span><span class="subtle-pill">{{report.catalogYear||t('Requirement year unknown')}}</span></div>
     <div class="credit-headline"><strong>{{value(report.totals?.completed)}}</strong><span>/ {{value(report.totals?.required)}} {{t("credits completed")}}</span><span v-if="totals" class="percent">{{totals.percent}}%</span></div>
     <div v-if="totals" class="credit-bar" role="img" :aria-label="`${report.totals.completed} completed, ${report.totals.inProgress} in progress, ${totals.additional} additional credits after current courses, ${totals.remaining} total credits still to earn`"><span class="bar-complete" :style="{width:totals.completeWidth+'%'}"></span><span class="bar-progress" :style="{width:totals.progressWidth+'%'}"></span></div>
     <p v-else class="unknown-note">{{t("Some credit totals need confirmation. We haven’t filled in missing values.")}}</p>
     <div class="credit-legend"><div><span class="legend-dot legend-complete"></span><span>{{t("Completed")}}<strong>{{value(report.totals?.completed)}}</strong></span></div><div><span class="legend-dot legend-progress"></span><span>{{report.totals?.includesPlannedTerms?t('In progress / planned'):t('In progress')}}<strong>{{value(report.totals?.inProgress)}}</strong></span></div><div><span class="legend-dot gray"></span><span>{{t("After current courses")}}<strong>{{value(totals?.additional)}}</strong></span></div></div>
     <p v-if="totals" class="panel-footnote"><strong>{{value(totals.remaining)}} {{t("credits still to earn")}}</strong>{{t(", including current courses.")}} {{t("Gray assumes current courses are passed.")}}</p>
    </div>
    <CreditChart :totals="report.totals"/>
   </section>
   <div v-if="report.warnings?.length && !report.demo" class="warnings"><p v-for="w in report.warnings" :key="w"><Info :size="15"/>{{t(w)}}</p></div>
   <CatalogComparison v-if="report.catalog && !report.demo" :catalog="report.catalog"/>
   <RequirementChart :requirements="report.requirements" @select="openRequirement"/>
   <section class="requirements-section"><div class="section-heading"><div><h2>{{t("Your requirements")}}</h2><p>{{completedCount}} {{t("of")}} {{report.requirements.length}} {{t("requirements covered (including planned)")}}</p></div><div class="filters" aria-label="Filter requirements"><button v-for="(label,key) in {all:'All',remaining:'To complete',complete:'Covered'}" :key="key" :class="{active:filter===key}" @click="filter=key" :aria-pressed="filter===key">{{t(label)}}</button></div></div>
    <div class="requirements-list"><article v-for="item in visible" :key="item.id" :id="`requirement-${item.id}`" class="requirement" :class="{expanded:expanded===item.id}"><button class="requirement-summary" @click="expanded=expanded===item.id?null:item.id" :aria-expanded="expanded===item.id"><span class="requirement-icon" :class="item.status"><Check v-if="coveredByPlan(item)" :size="19"/><Info v-else-if="item.status==='review'" :size="19"/><BookOpen v-else :size="19"/></span><span class="requirement-title">{{item.title}}<span v-if="item.required!=null">{{value(item.completed)}} {{t("of")}} {{item.required}} {{t("credits completed")}}<span v-if="item.inProgress"> · {{item.inProgress}} {{t("in progress")}}</span></span><span v-else>{{t("More information needed")}}</span></span><span class="status" :class="item.status">{{t(coveredByPlan(item)&&item.status!=='complete'?'Covered by planned courses':statuses[item.status]||'Needs review')}}</span><ChevronDown class="expand-chevron" :size="18"/></button><div v-if="expanded===item.id" class="requirement-detail"><p>{{t(item.description)}}</p><AppliedCourses :item="item"/><p v-if="coveredByPlan(item)&&item.status!=='complete'" class="muted">{{t("No extra courses needed if planned courses are passed.")}}</p><section v-if="!coveredByPlan(item)" class="requirement-courses"><h3>{{t("Courses for this requirement")}}</h3><p v-if="item.status==='complete'" class="muted">{{t("This requirement is already marked complete.")}}</p><p v-else-if="progress(item)?.additional>0" class="course-gap"><strong>{{value(progress(item).additional)}} {{t("needed after current courses")}}</strong></p><p v-else-if="progress(item) && item.inProgress>0" class="muted">{{t("Pending current courses")}}</p><template v-if="item.courses?.length"><p class="course-source">{{t("Options listed in your PDF")}}</p><ul class="report-course-list"><li v-for="course in item.courses" :key="course">{{course}}</li></ul><p class="muted">{{t("Follow the report’s selection rules; these are not confirmed remaining courses.")}}</p></template><p v-else class="muted">{{t("No course options listed in the PDF. Check the catalog below or ask about this requirement.")}}</p><button type="button" class="outline-button" @click="askCourses(item)">{{t("Help me choose courses")}} <ArrowUpRight :size="15"/></button></section><CatalogComparison v-if="item.catalogMatch" :match="item.catalogMatch"/><div class="requirement-actions"><button v-if="item.evidence?.quote" @click="source=item"><FileText :size="14"/> {{item.evidence.verified?t('View source'):t('Review unverified source')}} {{t("· p.")}} {{item.evidence.page||'?'}}</button><button @click="askAbout(item)"><MessageCircle :size="14"/> {{t("Ask about this")}}</button></div></div></article><div v-if="!visible.length" class="empty-state">{{t("No requirements in this view.")}}</div></div>
   </section>
   <MinorPlanner v-if="!report.demo && report.minorAssessment" :assessment="report.minorAssessment" :inventory="report.courseInventory" @ask="askMathMinor"/>
   <div class="report-footer"><span><FileText :size="15"/> {{report.fileName}}</span><div class="report-tools"><button @click="download"><Download :size="15"/> {{t("Export analysis")}}</button><button v-if="!report.demo" class="delete-link" @click="confirmDelete(report)">{{t("Delete report")}}</button></div></div>
   <button v-if="!chatOpen" class="chat-launcher" @click="chatOpen=!chatOpen"><MessageCircle :size="19"/> {{t("Ask your report")}} <span>✧</span></button>
  </main>
  <footer class="site-footer"><span>LionPathway <span class="footer-dot">•</span> <span class="footer-team">{{t("Built by BLS")}}</span></span><span>{{t("Independent of Penn State. Confirm academic decisions with your adviser.")}}</span></footer>
  <div v-if="source" class="modal-backdrop" @click.self="source=null"><section class="source-modal" role="dialog" aria-modal="true" :aria-label="t(&quot;Report evidence&quot;)"><div class="panel-heading"><span><FileText :size="18"/> {{t("Report evidence")}}</span><button @click="source=null" :aria-label="t(&quot;Close evidence&quot;)"><X :size="20"/></button></div><h2>{{source.title}}</h2><p class="muted">{{report.demo?t('Illustrative source'):t('Extracted source')}} {{t("· Page")}} {{source.evidence.page||'unknown'}}</p><blockquote>{{source.evidence.quote}}</blockquote><p class="panel-footnote">{{report.demo?'Illustrative sample evidence; no personal PDF was analyzed.':source.evidence.verified?'This text matches the extracted report page. This does not independently verify the academic interpretation.':'This citation could not be matched to the extracted page. Please check the original PDF.'}}</p></section></div>
  <div v-if="historyOpen" class="modal-backdrop" @click.self="historyOpen=false"><section class="source-modal history-modal" role="dialog" aria-modal="true" :aria-label="t(&quot;Saved reports&quot;)"><div class="panel-heading"><span><BookOpen :size="18"/> {{t("Your saved reports")}}</span><button @click="historyOpen=false" :aria-label="t(&quot;Close saved reports&quot;)"><X :size="20"/></button></div><p v-if="historyBusy" class="loading-message" role="status">{{t("Loading reports…")}}</p><p v-else-if="!savedReports.length" class="empty-state">{{t("No saved reports yet. Upload a report to get started.")}}</p><div v-for="saved in savedReports" :key="saved.id" class="saved-report-row"><button class="saved-report" :disabled="reportBusy||deleteBusy" @click="openReport(saved.id)"><FileText :size="19"/><span><strong>{{saved.major||t('Academic report')}}</strong><small>{{saved.filename}}</small><small>{{new Date(saved.createdAt).toLocaleDateString()}}</small></span><ChevronRight :size="17"/></button><button class="saved-report-delete" :disabled="deleteBusy||reportBusy" :aria-label="`Delete ${saved.filename||saved.major||'report'}`" @click="confirmDelete(saved)"><Trash2 :size="17"/><span>{{t("Delete")}}</span></button></div></section></div>
  <div v-if="deleteConfirm" class="modal-backdrop" @click.self="!deleteBusy && (deleteConfirm=false)"><section class="source-modal" role="dialog" aria-modal="true" :aria-label="t(&quot;Delete report&quot;)"><h2>{{t("Delete this report?")}}</h2><p class="delete-target">{{deleteTarget?.name}}</p><p class="muted">{{t("The saved analysis and its conversation will be permanently removed. You can upload the PDF again later.")}}</p><div class="confirm-actions"><button class="outline-button" :disabled="deleteBusy" @click="deleteConfirm=false">{{t("Keep report")}}</button><button class="danger-button" :disabled="deleteBusy" @click="deleteReport">{{deleteBusy?t('Deleting…'):t('Delete report')}}</button></div></section></div>
  <aside v-if="chatOpen && report" class="chat-panel" role="dialog" :aria-label="t(&quot;Ask your report&quot;)"><div class="chat-header"><span class="chat-symbol">✧</span><div><strong>{{t("Ask your report")}}</strong><span v-if="report.demo">{{t("Sample conversation")}}</span></div><button @click="chatOpen=false" :aria-label="t(&quot;Close chat&quot;)"><X :size="20"/></button></div><div ref="chatBody" class="chat-body"><div v-if="!messages.length" class="suggestions"><button v-for="q in ['How many credits are left?','Explain Department List','Is my minor included?']" :key="q" @click="question=t(q);send()">{{t(q)}}<ArrowUpRight :size="14"/></button></div><div v-for="(msg,i) in messages" :key="i" :class="['chat-message',msg.role]"><ChatAnswer v-if="msg.role==='assistant'" :text="msg.text" :research="msg.webResearch"/><template v-else>{{msg.text}}</template></div><div v-if="chatBusy" class="chat-thinking" role="status"><LoaderCircle :size="16" class="spin"/> {{webSearch&&!report.demo?t("Checking your report and web sources…"):t("Reading your report…")}}</div></div><label v-if="!report.demo" class="chat-web-toggle"><input type="checkbox" v-model="webSearch" :disabled="chatBusy">{{t("Search the web when needed")}}<span>{{t("Your question may be used for search. Do not include personal information.")}}</span></label><form class="chat-form" @submit.prevent="send"><label for="chat-question" class="sr-only">{{t("Question about your report")}}</label><textarea id="chat-question" v-model="question" @keydown="submitOnEnter" rows="3" maxlength="2000" :title="t(&quot;Enter to send · Shift+Enter for a new line&quot;)" :placeholder="t(&quot;Ask about a requirement…&quot;)"></textarea><button :disabled="chatBusy||!question.trim()" :aria-label="t(&quot;Send question&quot;)"><ArrowUp :size="19"/></button></form><p class="chat-disclaimer">{{t("Enter to send · Shift+Enter for a new line")}}</p></aside>
 </div>
</template>
