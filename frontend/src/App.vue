<script setup>
import {t as translate,locale,useInputLanguage,pageText,displayLanguage} from './i18n.js';
import {computed,inject,nextTick,ref,watch,onMounted,onUnmounted} from 'vue';
import {useAuth0} from '@auth0/auth0-vue';
import {ArrowUp,ArrowUpRight,ArrowRight,Plus,X,FileText,Check,ChevronDown,ChevronRight,BookOpen,MessageCircle,ShieldCheck,LoaderCircle,LogOut,Download,Info,Search,RotateCcw,Trash2,Sun,Moon,UserRound,PanelRightOpen,Sparkles,PanelLeftClose,PanelLeftOpen,Ellipsis} from 'lucide-vue-next';
import sample from './sample.json';
import CatalogComparison from './components/CatalogComparison.vue';
import CreditChart from './components/CreditChart.vue';
import ChatAnswer from './components/ChatAnswer.vue';
import AppliedCourses from './components/AppliedCourses.vue';
import MinorPlanner from './components/MinorPlanner.vue';
import RequirementChart from './components/RequirementChart.vue';
import WelcomePage from './components/WelcomePage.vue';
import LoadingMark from './components/LoadingMark.vue';
import BrandMark from './components/BrandMark.vue';
import ChatSidebar from './components/ChatSidebar.vue';
import AdvisorView from './components/AdvisorView.vue';
import {viewTitle,progressView} from './advisor-view.js';
import {apiRequest} from './api.js';
import {sessionStatus} from './auth-config.js';
import {progress,coveredByPlan,filterRequirements,validateFile,hasMathMinorInterest} from './model.js';
const appShell=ref(null),fixedHeader=ref(null),fixedFooter=ref(null);
let chromeObserver;
onMounted(()=>{
 const measure=()=>{
  for(const [element,property] of [[fixedHeader.value,'--app-header-height'],[fixedFooter.value,'--app-footer-height']]){
   const height=element?.getBoundingClientRect().height;
   if(height)appShell.value?.style.setProperty(property,`${height}px`);
  }
  const themeBounds=fixedHeader.value?.querySelector('.theme-toggle')?.getBoundingClientRect();
  if(themeBounds?.width)appShell.value?.style.setProperty('--chat-right-edge',`${Math.max(0,document.documentElement.clientWidth-themeBounds.right)}px`);
 };
 measure();
 if(typeof ResizeObserver!=='undefined'){chromeObserver=new ResizeObserver(measure);chromeObserver.observe(fixedHeader.value);chromeObserver.observe(fixedFooter.value);const controls=fixedHeader.value?.querySelector('.header-right');if(controls)chromeObserver.observe(controls);}
});
onUnmounted(()=>chromeObserver?.disconnect());
const theme=ref(document.documentElement.dataset.theme||'light');
let themeTransitionTimer;
onUnmounted(()=>{clearTimeout(themeTransitionTimer);document.documentElement.classList.remove('theme-transition');});
function toggleTheme(){
 clearTimeout(themeTransitionTimer);
 if(!window.matchMedia('(prefers-reduced-motion: reduce)').matches){
  document.documentElement.classList.add('theme-transition');
  void document.documentElement.offsetHeight;
  themeTransitionTimer=setTimeout(()=>document.documentElement.classList.remove('theme-transition'),650);
 }
 theme.value=theme.value==='dark'?'light':'dark';
 document.documentElement.dataset.theme=theme.value;
 document.querySelector('meta[name="theme-color"]')?.setAttribute('content',theme.value==='dark'?'#111214':'#ffffff');
 try{localStorage.setItem('lionpathway-theme',theme.value);}catch{}
}
const introOpen=ref(window.location.hash==='#/intro');
const configured=inject('authConfigured');
const auth=configured?useAuth0():null;
const report=ref(null),intent=ref(''),file=ref(null),fileInput=ref(null),busy=ref(false),error=ref(''),notice=ref(''),dragging=ref(false),filter=ref('all'),expanded=ref(null),source=ref(null),reportPanelOpen=ref(false),uploadOpen=ref(false),question=ref(''),messages=ref([]),chatBusy=ref(false),chatBody=ref(null),consent=ref(false);
let savedNoticeTimer;
watch(notice,message=>{
 clearTimeout(savedNoticeTimer);
 if(message==='Report saved to your profile.')savedNoticeTimer=setTimeout(()=>{notice.value='';},4000);
},{flush:'sync'});
onUnmounted(()=>clearTimeout(savedNoticeTimer));
const t=text=>pageText(text,Boolean(report.value)&&!introOpen.value);
watch([locale,report,introOpen],()=>{document.documentElement.lang=displayLanguage(Boolean(report.value)&&!introOpen.value);},{immediate:true});
const webSearch=ref(true);
// Live agent steps for the answer being written, polled once a second from /chat/progress.
const agentSteps=ref([]),chatElapsed=ref(0),agentProgress=computed(()=>progressView(agentSteps.value));
// Long-term memories the agent saved for this account; listed from the chat settings menu.
const memories=ref([]),memoryOpen=ref(false),memoryError=ref('');
async function openMemories(){memoryOpen.value=true;memoryError.value='';try{memories.value=await request('/api/memories');}catch(e){memoryError.value=e.message;}}
async function forgetMemory(item){try{await request(`/api/memories/${item.id}`,{method:'DELETE'});if(item.action)item.action='forgotten';memories.value=memories.value.filter(m=>m.id!==item.id);}catch(e){memoryError.value=e.message;error.value=e.message;}}
const chatSettingsOpen=ref(false),chatSettings=ref(null);
function closeChatSettings(event){if(chatSettingsOpen.value&&!chatSettings.value?.contains(event.target))chatSettingsOpen.value=false;}
onMounted(()=>document.addEventListener('pointerdown',closeChatSettings));
onUnmounted(()=>document.removeEventListener('pointerdown',closeChatSettings));
watch(report,()=>{chatSettingsOpen.value=false;});
const clearChatOpen=ref(false),clearChatBusy=ref(false),clearChatError=ref('');
function confirmClearChat(){if(chatBusy.value||busy.value||clearChatBusy.value||!messages.value.length)return;clearChatError.value='';clearChatOpen.value=true;}
async function clearChat(){
 if(clearChatBusy.value||chatBusy.value||!report.value)return;
 const current=report.value;clearChatBusy.value=true;clearChatError.value='';
 try{
  if(!current.demo)await request(`/api/reports/${current.id}/messages`,{method:'DELETE'});
  if(report.value===current){messages.value=[];question.value='';}if(!current.demo)rememberLastQuestion(current.id,null);
  clearChatOpen.value=false;
 }catch(e){clearChatError.value=e.message;}finally{clearChatBusy.value=false;}
}

const profileName=computed(()=>auth?.user.value?.name||auth?.user.value?.nickname||'');
const profileEmail=computed(()=>auth?.user.value?.email||'');
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
const savedReports=ref([]),historyBusy=ref(false),historyLoaded=ref(false),historyOpen=ref(window.location.hash==='#/profile'),reportBusy=ref(false),deleteConfirm=ref(false),deleteBusy=ref(false),service=ref(null),serviceError=ref('');
const signedIn=computed(()=>Boolean(auth?.isAuthenticated.value));
const authLoading=computed(()=>Boolean(auth?.isLoading.value));
const onboarding=computed(()=>signedIn.value&&historyLoaded.value&&!savedReports.value.length&&(!report.value||report.value.demo));
const chatListAvailable=computed(()=>signedIn.value&&!introOpen.value&&historyLoaded.value&&savedReports.value.length>0&&Boolean(report.value)&&!report.value.demo&&!historyOpen.value&&!onboarding.value);
const chatListOpen=ref((()=>{try{return localStorage.getItem('onlyplans-chat-list')!=='hidden';}catch{return true;}})());
// Opening the report panel tucks the chat list away without changing the saved preference.
const chatListTucked=ref(false);
watch(reportPanelOpen,open=>{chatListTucked.value=open;});
function toggleChatList(){if(chatListTucked.value){chatListTucked.value=false;if(chatListOpen.value)return;}chatListOpen.value=!chatListOpen.value;try{localStorage.setItem('onlyplans-chat-list',chatListOpen.value?'shown':'hidden');}catch{}}
const chatListVisible=computed(()=>chatListAvailable.value&&chatListOpen.value&&!chatListTucked.value);
function rememberLastQuestion(id,text){const item=savedReports.value.find(row=>row.id===id);if(item)item.lastQuestion=text?text.slice(0,120):null;}
const canDeleteReport=computed(()=>historyLoaded.value&&!historyBusy.value&&savedReports.value.length>1);
function syncPage(){
 introOpen.value=window.location.hash==='#/intro';
 if(introOpen.value){historyOpen.value=false;reportPanelOpen.value=false;return;}
 const mustSetup=onboarding.value||busy.value;
 historyOpen.value=signedIn.value&&(mustSetup||window.location.hash==='#/profile');
 if(historyOpen.value&&window.location.hash!=='#/profile')window.history.replaceState(null,'','#/profile');
}
watch(onboarding,required=>{if(required&&!introOpen.value){historyOpen.value=true;window.history.replaceState(null,'','#/profile');}});
watch([historyOpen,introOpen],([open,intro])=>{const hash=intro?'#/intro':open?'#/profile':'#/';if(window.location.hash!==hash)window.history.pushState(null,'',hash);});
onMounted(()=>window.addEventListener('hashchange',syncPage));
onUnmounted(()=>window.removeEventListener('hashchange',syncPage));
function openProfile(){introOpen.value=false;historyOpen.value=true;reportPanelOpen.value=false;void loadHistory();}
const session=computed(()=>sessionStatus(configured,authLoading.value,signedIn.value));
async function checkService(){try{const r=await fetch(base+'/api/health');if(!r.ok)throw Error();service.value=await r.json();serviceError.value='';}catch{serviceError.value='The analysis server is not connected. Start the Spring Boot backend, then retry.';}}
onMounted(()=>{checkService();const draft=sessionStorage.getItem('lionpathway-goal');if(draft){intent.value=draft;sessionStorage.removeItem('lionpathway-goal');notice.value='You’re signed in. Attach your PDF again to begin the analysis.';}});
watch(session,async state=>{
 if(state==='signed-in'){
  const loaded=await loadHistory();
  if(loaded && signedIn.value && !report.value && savedReports.value.length){
   await openReport(savedReports.value[0].id,false,true);
   if(report.value && intent.value.trim())await answerInitialQuestion(intent.value);
  }
 }else if(state==='signed-out'){historyOpen.value=false;historyLoaded.value=false;savedReports.value=[];if(report.value&&!report.value.demo)reset();}
},{immediate:true});
watch(()=>auth?.error.value,e=>{if(e)error.value='Sign-in could not finish: '+(e.message||'Check Auth0 allowed URLs and API audience.');},{immediate:true});
async function loadHistory(){const owner=auth?.user.value?.sub;historyBusy.value=true;error.value='';try{const rows=await request('/api/reports');if(!Array.isArray(rows))throw Error('The report list could not be loaded. Please retry.');if(!signedIn.value||auth?.user.value?.sub!==owner)return false;savedReports.value=rows;historyLoaded.value=true;return true;}catch(e){historyLoaded.value=false;error.value=e.message;return false;}finally{historyBusy.value=false;}}
async function openReport(id,showReport=false,preservePage=false){if(chatBusy.value||busy.value||reportBusy.value||clearChatBusy.value)return;const owner=auth?.user.value?.sub;reportBusy.value=true;error.value='';try{const next=await request(`/api/reports/${id}`);const history=await request(`/api/reports/${id}/messages`);if(!signedIn.value||auth?.user.value?.sub!==owner)return;report.value=next;chatBusy.value=false;messages.value=history.map(m=>({role:m.role,text:m.content,webResearch:m.webResearch,view:m.view,viewOpen:false}));if(!preservePage)historyOpen.value=false;filter.value='all';expanded.value=null;source.value=null;reportPanelOpen.value=showReport;uploadOpen.value=false;window.scrollTo(0,0);}catch(e){error.value=e.message;}finally{reportBusy.value=false;}}
function confirmDelete(item){if(deleteBusy.value||item.demo||!canDeleteReport.value)return;deleteTarget.value={id:item.id,name:item.fileName||item.filename||item.major||'Academic report'};deleteConfirm.value=true;}
async function deleteReport(){
 if(!deleteTarget.value||deleteBusy.value||!canDeleteReport.value)return;
 const id=deleteTarget.value.id;deleteBusy.value=true;error.value='';
 try{
  await request(`/api/reports/${id}`,{method:'DELETE'});
  savedReports.value=savedReports.value.filter(item=>item.id!==id);
  if(report.value?.id===id){reset();if(savedReports.value.length)await openReport(savedReports.value[0].id,false,true);}
  deleteConfirm.value=false;deleteTarget.value=null;
  showDeletionNotice();
 }catch(e){await loadHistory();error.value=e.message;}finally{deleteBusy.value=false;}
}

const totals=computed(()=>progress(report.value?.totals));
const visible=computed(()=>filterRequirements(report.value?.requirements||[],filter.value));
const completedCount=computed(()=>report.value?.requirements.filter(coveredByPlan).length||0);
const statuses={complete:'Complete',in_progress:'In progress',remaining:'Remaining',review:'Needs review'};
async function headers(){if(!configured||!auth.isAuthenticated.value)throw Error('Sign in to analyze your personal report.');return {Authorization:`Bearer ${await auth.getAccessTokenSilently()}`};}
function request(path,options={},settings={}){return apiRequest(base+path,options,{getHeaders:headers,...settings});}
function pick(f){if(busy.value)return;error.value='';const problem=validateFile(f);if(problem){error.value=problem;return;}file.value=f;}
function drop(event){dragging.value=false;if(event.dataTransfer.files.length)pick(event.dataTransfer.files[0]);}
async function signIn(){error.value='';if(!configured){notice.value='Live sign-in needs Auth0 configuration. You can explore the sample without an account.';return;}try{if(intent.value.trim())sessionStorage.setItem('lionpathway-goal',intent.value);await auth.loginWithRedirect();}catch(e){error.value=e.message;}}
function signOut(){auth?.logout({logoutParams:{returnTo:window.location.origin}});}
function demo(showReport=false){introOpen.value=false;report.value=structuredClone(sample);chatBusy.value=false;reportPanelOpen.value=Boolean(showReport);uploadOpen.value=false;messages.value=[];filter.value='all';error.value='';notice.value='';window.scrollTo(0,0);void answerInitialQuestion(intent.value);}
function reset(){report.value=null;messages.value=[];chatBusy.value=false;reportPanelOpen.value=false;uploadOpen.value=false;source.value=null;error.value='';expanded.value=null;deleteConfirm.value=false;}
function home(){if(busy.value||onboarding.value)return;introOpen.value=false;if(report.value?.demo){reset();return;}historyOpen.value=false;uploadOpen.value=false;reportPanelOpen.value=false;}
function introduce(){
 if(busy.value)return;
 introOpen.value=true;historyOpen.value=false;reportPanelOpen.value=false;chatSettingsOpen.value=false;
 window.scrollTo(0,0);
}
async function startPlanning(){
 if(authLoading.value)return;
 if(!signedIn.value){await signIn();return;}
 if(historyBusy.value||reportBusy.value)return;
 if(!historyLoaded.value&&!await loadHistory()){openProfile();return;}
 if(!savedReports.value.length){openProfile();return;}
 if(!report.value||report.value.demo){await openReport(savedReports.value[0].id);if(!report.value||report.value.demo)return;}
 home();window.scrollTo(0,0);
}
async function addReport(){if(busy.value||chatBusy.value)return;historyOpen.value=true;uploadOpen.value=true;error.value='';if(report.value)intent.value='';file.value=null;consent.value=false;await nextTick();fileInput.value?.click();}
async function analyze(){const submittedIntent=intent.value.trim();let analysisSucceeded=false;useInputLanguage(submittedIntent);error.value='';if(authLoading.value){error.value='Sign-in is still loading. Please try again in a moment.';return;}if(!configured){error.value='Live PDF analysis requires Auth0, Gemini, and the backend. Use “Explore a sample” to preview the experience.';return;}if(!auth.isAuthenticated.value){await signIn();return;}if(!file.value){fileInput.value.click();return;}if(!consent.value){error.value='Please allow your report text to be sent to the AI providers for analysis.';return;}if(busy.value)return;busy.value=true;elapsed.value=0;uploadStage.value='auth';uploadController=new AbortController();const started=Date.now();const ticker=setInterval(()=>elapsed.value=Math.floor((Date.now()-started)/1000),1000);try{const form=new FormData();form.append('file',file.value);form.append('intent',submittedIntent);form.append('language',locale.value);report.value=await request('/api/reports',{method:'POST',body:form},{signal:uploadController.signal,onStage:stage=>uploadStage.value=stage});messages.value=[];chatBusy.value=false;reportPanelOpen.value=false;uploadOpen.value=false;filter.value='all';expanded.value=null;file.value=null;consent.value=false;notice.value='Report saved to your profile.';await loadHistory();historyOpen.value=false;window.scrollTo(0,0);analysisSucceeded=true;}catch(e){error.value=e.message;}finally{clearInterval(ticker);uploadController=null;busy.value=false;}if(analysisSucceeded)await answerInitialQuestion(submittedIntent);}
async function answerInitialQuestion(text){
 if(!text.trim())return;
 question.value=text.trim();
 await send();
}
async function openRequirement(item){
 filter.value='all';expanded.value=item.id;
 await nextTick();
 const target=document.getElementById(`requirement-${item.id}`);
 target?.scrollIntoView({behavior:window.matchMedia('(prefers-reduced-motion: reduce)').matches?'auto':'smooth',block:'center'});
 target?.querySelector('.requirement-summary')?.focus({preventScroll:true});
}
function focusQuestion(){if(historyOpen.value)home();if(window.matchMedia('(max-width:1000px)').matches)reportPanelOpen.value=false;nextTick(()=>document.getElementById('chat-question')?.focus());}
function askAbout(item){question.value=translate('What does “{title}” mean, and what is left to complete?').replace('{title}',item.title);focusQuestion();}
function askCourses(item){question.value=translate('Which courses can satisfy “{title}”? Explain course options, prerequisites, remaining credits and any uncertain approvals using my report and catalog.').replace('{title}',item.title);focusQuestion();}
function askMathMinor(){question.value=translate('Based on my completed and planned courses, what would I need for the Mathematics minor? Explain additional credits, course options, prerequisites and overlap with my major.');focusQuestion();}
function demoAnswer(q){if(locale.value!=='en')return translate('This sample has 72 completed credits, 15 in progress and 33 additional credits needed out of 120. Meeting the credit total does not satisfy every requirement. This is a sample answer, not a live AI response.');let item=report.value.requirements.find(r=>q.toLowerCase().includes(r.title.toLowerCase()));if(!item&&/minor|math/i.test(q))item=report.value.requirements.find(r=>r.id==='minor');if(item)return `${item.description}\n\nSample source: page ${item.evidence.page}. This is an illustrative answer, not a live Gemini response.`;if(/credit|left|remaining/i.test(q))return 'In this sample, 72 of 120 credits are completed, 15 are in progress, and 33 remain beyond current courses. Finishing the credit total does not automatically satisfy every requirement.\n\nThis is an illustrative answer, not a live Gemini response.';return 'This is sample mode. Try asking about Department List, Supportive Electives, the Mathematics minor, or remaining credits. Live Gemini answers become available when the services are configured.';}
async function send(){const q=question.value.trim(),contextReport=report.value;if(!q||chatBusy.value||clearChatBusy.value||!contextReport)return;useInputLanguage(q);question.value='';messages.value.push({role:'user',text:q});if(!contextReport.demo)rememberLastQuestion(contextReport.id,q);chatBusy.value=true;agentSteps.value=[];chatElapsed.value=0;const requestId=crypto.randomUUID(),started=Date.now();let polling=false;const poll=contextReport.demo?null:setInterval(async()=>{chatElapsed.value=Math.floor((Date.now()-started)/1000);if(polling)return;polling=true;try{const p=await request(`/api/reports/${contextReport.id}/chat/progress/${requestId}`,{},{timeoutMs:5000});if(chatBusy.value&&report.value===contextReport)agentSteps.value=p.steps||[];}catch{}finally{polling=false;}},1000);try{const result=contextReport.demo?{answer:demoAnswer(q)}:await request(`/api/reports/${contextReport.id}/chat`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:q,language:locale.value,webSearch:webSearch.value,requestId})},{timeoutMs:160000});if(report.value===contextReport)messages.value.push({role:'assistant',text:result.answer,webResearch:result.webResearch,view:result.view,viewOpen:Boolean(result.view),memoryChanges:result.memoryChanges});}catch(e){if(report.value===contextReport)messages.value.push({role:'error',text:e.message});}finally{if(poll)clearInterval(poll);agentSteps.value=[];if(report.value===contextReport)chatBusy.value=false;await nextTick();chatBody.value?.scrollTo({top:chatBody.value.scrollHeight,behavior:'smooth'});}}
function download(){const blob=new Blob([JSON.stringify(report.value,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='lionpathway-analysis.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function value(n){return Number.isFinite(n)?n:'—';}
</script>

<template>
 <div ref="appShell" class="app-shell" :class="{'report-visible':reportPanelOpen && report && !historyOpen,'chats-visible':chatListVisible}" @keydown.esc="chatSettingsOpen=false;source=null;reportPanelOpen=false;if(!busy)uploadOpen=false;deleteConfirm=false;if(!clearChatBusy)clearChatOpen=false">
  <header ref="fixedHeader" class="topbar">
   <button class="brand" @click="introduce" aria-label="OnlyPlans home"><BrandMark :size="40"/><span>Only<span class="brand-accent">Plans</span></span></button>
   <div class="header-right"><button type="button" class="theme-toggle" :aria-label="theme==='dark'?'Switch to light mode':'Switch to dark mode'" :title="theme==='dark'?'Switch to light mode':'Switch to dark mode'" @click="toggleTheme"><Sun v-if="theme==='dark'" :size="22"/><Moon v-else :size="22"/></button><span v-if="session==='restoring'" class="session-restoring" role="status"><LoadingMark :size="24"/> {{t("Checking session…")}}</span><button v-if="session==='signed-in'" class="sign-in" @click="openProfile"><UserRound :size="21"/> {{t("Profile")}}</button><button v-if="session==='signed-out'" class="sign-in" @click="signIn">{{t("Sign in")}} <ArrowUpRight :size="15"/></button></div>
  </header>
  <div v-if="deletionNotice" class="success-toast" role="status" aria-live="polite" aria-atomic="true"><Check :size="18"/><span>{{t(deletionNotice)}}</span><button type="button" @click="dismissDeletionNotice" aria-label="Dismiss deletion notification"><X :size="16"/></button></div>
  <div v-if="notice" class="notice" role="status">{{t(notice)}}<button @click="notice=''" :aria-label="t(&quot;Dismiss&quot;)"><X :size="16"/></button></div>
  <div v-if="error && (report || historyOpen || !signedIn)" class="global-error error" role="alert">{{t(error)}}<button @click="error=''" :aria-label="t(&quot;Dismiss error&quot;)"><X :size="16"/></button></div>
  <WelcomePage v-if="introOpen || (!signedIn && !authLoading && !report)" @signin="startPlanning" @sample="demo"/>
  <main v-if="!introOpen && (signedIn || authLoading) && !report && !onboarding && !historyOpen" class="landing">
   <section class="hero">
    <form v-if="!signedIn" class="search-card" @submit.prevent="signIn"><label class="sr-only" for="landing-question">Your question</label><textarea id="landing-question" v-model="intent" rows="2" maxlength="1000" @keydown="submitOnEnter" placeholder="Ask about courses, credits or a minor…"></textarea><div class="search-toolbar"><button type="submit" class="outline-button">Sign in <ArrowUpRight :size="16"/></button></div></form>
    <p v-if="authLoading||historyBusy||reportBusy" role="status" class="loading-message branded-loading account-loading"><LoadingMark :size="80"/>Loading your account…</p>
    <div v-if="error" class="error" role="alert"><p>{{error}}</p><button v-if="signedIn" class="outline-button" @click="loadHistory">Retry</button></div>
    <div v-if="!signedIn" class="hero-actions"><button @click="demo">Explore a sample <ArrowRight :size="14"/></button></div>
   </section>
  </main>
  <main v-if="!introOpen && ((signedIn && historyOpen) || report)" :class="{'profile-page':historyOpen,workspace:report && !historyOpen && !onboarding}">
  <section v-if="signedIn && historyOpen" :aria-label="t('Profile')">
   <div class="profile-page-heading"><h1>{{t("Profile")}}</h1></div>
<div class="profile-identity"><span class="profile-avatar"><UserRound :size="32"/></span><div class="profile-details"><strong>{{profileName||t("Profile")}}</strong><p>{{profileEmail}}</p></div><button type="button" class="saved-report-delete profile-signout" :disabled="busy||deleteBusy" @click="signOut">{{t("Sign out")}} <LogOut :size="17"/></button></div><div v-if="!reportPanelOpen || uploadOpen" class="profile-report-heading"><h3>{{t("My reports")}}</h3><button class="outline-button" :disabled="busy||chatBusy||historyBusy||reportBusy||!historyLoaded" @click="addReport"><Plus :size="16"/> {{t("Add new")}}</button></div><div v-if="error" class="error" role="alert"><p>{{t(error)}}</p><button class="outline-button" @click="loadHistory">{{t("Retry")}}</button></div><p v-if="historyBusy||reportBusy" class="loading-message branded-loading" role="status"><LoadingMark/>{{t("Loading reports…")}}</p><template v-if="!historyBusy && !reportBusy && historyLoaded && (!reportPanelOpen || uploadOpen)"><p v-if="historyLoaded && savedReports.length===1" class="panel-footnote">{{t("Keep at least one report. Add another report before deleting this one.")}}</p><div v-for="saved in savedReports" :key="saved.id" class="saved-report-row"><button class="saved-report" :disabled="reportBusy||deleteBusy||chatBusy||busy" @click="openReport(saved.id,true,true)" :aria-expanded="reportPanelOpen && report?.id===saved.id"><FileText :size="19"/><span><strong>{{saved.major||t('Academic report')}}</strong><small>{{saved.filename}}</small><small>{{new Date(saved.createdAt).toLocaleDateString()}}</small></span><ChevronRight :size="17"/></button><button class="saved-report-delete" :disabled="deleteBusy||reportBusy||chatBusy||busy||!canDeleteReport" :title="!canDeleteReport?t('Keep at least one report. Add another report before deleting this one.'):undefined" :aria-label="`Delete ${saved.filename||saved.major||'report'}`" @click="confirmDelete(saved)"><Trash2 :size="17"/><span>{{t("Delete")}}</span></button></div>
  <section v-if="onboarding || uploadOpen" class="profile-upload" :aria-label="t(onboarding?'Set up your profile':'Attach report')">
   <section class="hero"><button v-if="!onboarding" type="button" class="upload-close" :disabled="busy" @click="uploadOpen=false" aria-label="Close upload"><X :size="22"/></button>
    <h1>{{t(onboarding?'Add your first report':'Attach report')}}</h1>
    <p class="hero-description">{{t(onboarding?'Complete your account setup by adding a LionPATH PDF.':'Add your report to start a conversation.')}}</p>
    <form :class="onboarding?'first-report-upload':{'search-card':true,dragging}" @submit.prevent="analyze" @dragover.prevent="dragging=true" @dragleave.prevent="dragging=false" @drop.prevent="drop">
     <label v-if="!onboarding" class="sr-only" for="intent">{{t("Your major and the minor you are considering")}}</label>
     <textarea v-if="!onboarding" id="intent" @keydown="submitOnEnter" :title="t(&quot;Enter to analyze · Shift+Enter for a new line&quot;)" v-model="intent" maxlength="1000" :placeholder="t(&quot;I’m majoring in Computer Science, considering a Math minor…&quot;)" :disabled="busy" rows="2"></textarea>
     <div v-if="file" class="file-chip"><FileText :size="15"/><span>{{file.name}}</span><button type="button" :disabled="busy" @click="file=null" :aria-label="t(&quot;Remove PDF&quot;)"><X :size="14"/></button></div>
     <div :class="onboarding?'upload-button-row':'search-toolbar'"><button type="button" :class="onboarding?'outline-button':'attach-button'" @click="signedIn?fileInput.click():signIn()" :disabled="busy"><Plus :size="18"/> {{t("Attach report")}} <span>PDF</span></button><button v-if="!onboarding" type="submit" class="submit-button" :disabled="busy" :aria-label="t(&quot;Analyze report&quot;)"><LoaderCircle v-if="busy" class="spin" :size="21"/><ArrowUp v-else :size="22"/></button></div>
     <input ref="fileInput" :disabled="busy" type="file" accept=".pdf,application/pdf" class="sr-only" @change="e=>{if(e.target.files[0])pick(e.target.files[0]);e.target.value=''}">
    </form>
    <label v-if="file" class="consent"><input type="checkbox" v-model="consent"> {{t("Allow report text to be sent to Gemini for analysis and to Luminal and Gemini for chat answers. Remove personal identifiers before uploading.")}}</label>
    <button v-if="file" type="button" class="outline-button analyze-save" :disabled="busy||!consent" @click="analyze"><LoaderCircle v-if="busy" :size="18" class="spin"/><FileText v-else :size="18"/> {{t("Analyze and save")}}</button>
    <div v-if="file && (serviceError || (service && !service.live))" class="service-status"><Info :size="15"/><span>{{serviceError||'The server is in sample-only mode. Start it with the live profile to analyze PDFs.'}}</span><button type="button" @click="checkService">{{t("Retry")}}</button></div>
    <div v-if="busy" class="upload-progress"><p class="loading-message branded-loading" role="status"><LoadingMark/>{{uploadStage==='auth'?t('Verifying your sign-in…'):t('Uploading and analyzing your report…')}} {{elapsed}}s</p><p v-if="uploadStage==='analysis'" class="upload-hint">{{t("AI analysis can take up to 100 seconds. You can stop waiting at any time.")}}</p><button type="button" class="cancel-upload" @click="cancelUpload">{{t("Stop waiting")}}</button></div>
    <p v-if="error" class="error" role="alert">{{t(error)}}</p>

   </section>
  </section>
  </template>
  </section>
  <Transition name="chats-slide"><div v-if="chatListVisible" class="chat-sidebar-slot"><ChatSidebar :items="savedReports" :active-id="report.id" :disabled="busy||chatBusy||reportBusy||clearChatBusy" :t="t" @select="id=>openReport(id)" @add="addReport"/></div></Transition>
  <section v-if="report && !historyOpen && !onboarding" class="conversation-main" :aria-label="t(&quot;Ask your report&quot;)"><div class="chat-header"><button v-if="chatListAvailable" type="button" class="chat-list-toggle" :aria-expanded="chatListVisible" :aria-label="t(chatListVisible?'Hide chats':'Show chats')" :title="t(chatListVisible?'Hide chats':'Show chats')" @click="toggleChatList"><PanelLeftClose v-if="chatListVisible" :size="18"/><PanelLeftOpen v-else :size="18"/></button><div class="chat-title"><span class="chat-subtitle"><FileText :size="13"/><span>{{report.fileName||report.major}}<template v-if="report.demo"> · {{t("Sample conversation")}}</template></span></span></div><div class="chat-actions"><button v-if="!reportPanelOpen" type="button" class="outline-button chat-action report-toggle" @click="reportPanelOpen=true" :aria-expanded="false"><PanelRightOpen :size="16"/><span>{{t("View report")}}</span></button><div ref="chatSettings" class="chat-settings"><button type="button" class="chat-settings-button" aria-haspopup="true" :aria-expanded="chatSettingsOpen" :aria-label="t('Chat settings')" :title="t('Chat settings')" @click="chatSettingsOpen=!chatSettingsOpen"><Ellipsis :size="18"/></button><div v-if="chatSettingsOpen" class="chat-settings-menu"><label v-if="!report.demo" class="chat-settings-item chat-web-toggle"><input type="checkbox" v-model="webSearch" :disabled="chatBusy"><span><strong>{{t("Search the web when needed")}}</strong><small>{{t("Your question may be used for search. Do not include personal information.")}}</small></span></label><button v-if="!report.demo" type="button" class="chat-settings-item memory-menu-button" @click="chatSettingsOpen=false;openMemories()"><Sparkles :size="16"/><span>{{t("Memories")}}</span></button><button type="button" class="chat-settings-item clear-chat-button" :disabled="!messages.length||chatBusy||clearChatBusy||busy" @click="chatSettingsOpen=false;confirmClearChat()"><RotateCcw :size="16"/><span>{{t("Clear chat")}}</span></button></div></div></div></div><div ref="chatBody" class="chat-body" role="log" aria-live="polite" :aria-busy="chatBusy"><div v-if="!messages.length" class="conversation-welcome"><h1>{{t("What would you like to plan?")}}</h1><p>{{t("Your report is ready. Ask about courses, credits or a minor.")}}</p></div><div v-if="!messages.length" class="suggestions"><button v-for="q in ['How many credits are left?','Explain Department List','Is my minor included?']" :key="q" @click="question=t(q);send()">{{t(q)}}<ArrowUpRight :size="14"/></button></div><template v-for="(msg,i) in messages" :key="i"><div :class="['chat-message',msg.role]"><ChatAnswer v-if="msg.role==='assistant'" :text="msg.text" :research="msg.webResearch"/><template v-else>{{msg.text}}</template><p v-for="m in msg.memoryChanges||[]" :key="m.id+m.action" class="memory-chip"><span>{{m.action==='forgotten'?t("Removed from memory:"):t("Saved to memory:")}} {{m.content}}</span><button v-if="m.action==='saved'" type="button" @click="forgetMemory(m)">{{t("Undo")}}</button></p></div><AdvisorView v-if="msg.view && msg.viewOpen" class="chat-view" :view="msg.view" :inventory="report.courseInventory" @close="msg.viewOpen=false" @ask="askMathMinor"/><button v-else-if="msg.view" type="button" class="view-chip" @click="msg.viewOpen=true">{{t("Open screen:")}} {{t(viewTitle(msg.view))}}<ArrowUpRight :size="13"/></button></template><div v-if="chatBusy" class="chat-thinking agent-progress" role="status" aria-live="polite"><ul v-if="!report.demo && agentProgress.done.length" class="progress-done"><li v-for="d in agentProgress.done" :key="d"><Check :size="13"/> {{t(d)}}</li></ul><div class="progress-current"><LoadingMark :size="28"/> <span>{{report.demo?t("Reading your report…"):t(agentProgress.current)}}</span><small v-if="!report.demo && chatElapsed>=3">{{chatElapsed}}s</small></div></div></div><form class="chat-form" @submit.prevent="send"><Sparkles class="chat-form-icon" :size="20" aria-hidden="true"/><label for="chat-question" class="sr-only">{{t("Question about your report")}}</label><textarea id="chat-question" v-model="question" @keydown="submitOnEnter" rows="1" maxlength="2000" :title="t(&quot;Enter to send · Shift+Enter for a new line&quot;)" :placeholder="t(&quot;Ask about a requirement…&quot;)"></textarea><button :disabled="chatBusy||!question.trim()" :aria-label="t(&quot;Send question&quot;)"><ArrowUp :size="19"/></button></form><p class="chat-disclaimer">{{t("Confirm academic decisions with your adviser.")}}</p></section>
  <Transition name="report-slide" :css="!historyOpen">

  <component :is="historyOpen?'section':'aside'" v-if="report && reportPanelOpen && !onboarding && !uploadOpen && (!historyOpen || (!historyBusy && !reportBusy && historyLoaded))" class="dashboard" :class="historyOpen?'profile-report':'report-sidebar'" :aria-label="t('Your degree overview')">
   <div v-if="historyOpen" class="profile-report-actions"><button type="button" class="outline-button back-to-reports" @click="reportPanelOpen=false"><ArrowRight :size="18" class="back-arrow"/> {{t("My reports")}}</button><button type="button" class="outline-button profile-to-chat" :disabled="busy" @click="home"><MessageCircle :size="18"/> {{t("Ask your report")}}</button></div>
   <div class="report-sidebar-heading"><div><h2>{{t("Your degree overview")}}</h2><p>{{report.major||t('Major needs confirmation')}} · {{report.campus||t('Campus not specified')}}</p></div><button v-if="!historyOpen" class="outline-button" @click="reportPanelOpen=false" :aria-label="t('Hide report')"><X :size="20"/></button></div>
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
   <MinorPlanner v-if="!report.demo && report.minorAssessment && hasMathMinorInterest(report)" :assessment="report.minorAssessment" :inventory="report.courseInventory" @ask="askMathMinor"/>
   <div class="report-footer"><span><FileText :size="15"/> {{report.fileName}}</span><div class="report-tools"><button @click="download"><Download :size="15"/> {{t("Export analysis")}}</button><button v-if="!report.demo" class="delete-link" :disabled="!canDeleteReport||deleteBusy" :title="!canDeleteReport?t('Keep at least one report. Add another report before deleting this one.'):undefined" @click="confirmDelete(report)">{{t("Delete report")}}</button></div></div>
  </component>
  </Transition>
  </main>
  <footer ref="fixedFooter" class="site-footer"><span>OnlyPlans <span class="footer-dot">•</span> <span class="footer-team">{{t("Built by BLS")}}</span></span><span>{{t("Independent of Penn State.")}}</span></footer>
  <div v-if="source" class="modal-backdrop" @click.self="source=null"><section class="source-modal" role="dialog" aria-modal="true" :aria-label="t(&quot;Report evidence&quot;)"><div class="panel-heading"><span><FileText :size="18"/> {{t("Report evidence")}}</span><button @click="source=null" :aria-label="t(&quot;Close evidence&quot;)"><X :size="20"/></button></div><h2>{{source.title}}</h2><p class="muted">{{report.demo?t('Illustrative source'):t('Extracted source')}} {{t("· Page")}} {{source.evidence.page||'unknown'}}</p><blockquote>{{source.evidence.quote}}</blockquote><p class="panel-footnote">{{report.demo?'Illustrative sample evidence; no personal PDF was analyzed.':source.evidence.verified?'This text matches the extracted report page. This does not independently verify the academic interpretation.':'This citation could not be matched to the extracted page. Please check the original PDF.'}}</p></section></div>

  <div v-if="memoryOpen" class="modal-backdrop" @click.self="memoryOpen=false"><section class="source-modal memory-modal" role="dialog" aria-modal="true" :aria-label="t('Memories')"><div class="panel-heading"><span><Sparkles :size="18"/> {{t("Memories")}}</span><button type="button" @click="memoryOpen=false" :aria-label="t('Close screen')"><X :size="20"/></button></div><p class="muted">{{t("Things you told the assistant to remember. They apply to all your reports.")}}</p><p v-if="memoryError" class="error" role="alert">{{t(memoryError)}}</p><p v-else-if="!memories.length" class="muted">{{t("No saved memories yet.")}}</p><ul class="memory-list"><li v-for="m in memories" :key="m.id"><span>{{m.content}}</span><button type="button" @click="forgetMemory(m)" :aria-label="t('Delete memory')"><X :size="14"/></button></li></ul></section></div>
  <div v-if="clearChatOpen" class="modal-backdrop" @click.self="!clearChatBusy && (clearChatOpen=false)"><section class="source-modal" role="dialog" aria-modal="true" :aria-label="t('Clear this conversation?')"><h2>{{t("Clear this conversation?")}}</h2><p class="muted">{{t("Messages for this report will be permanently deleted. Your report will be kept.")}}</p><p v-if="clearChatError" class="error" role="alert">{{t(clearChatError)}}</p><div class="confirm-actions"><button type="button" class="outline-button" :disabled="clearChatBusy" @click="clearChatOpen=false">{{t("Keep conversation")}}</button><button type="button" class="danger-button" :disabled="clearChatBusy" @click="clearChat"><LoadingMark v-if="clearChatBusy" :size="20"/>{{t(clearChatBusy?'Clearing…':'Clear chat')}}</button></div></section></div>
  <div v-if="deleteConfirm" class="modal-backdrop" @click.self="!deleteBusy && (deleteConfirm=false)"><section class="source-modal" role="dialog" aria-modal="true" :aria-label="t(&quot;Delete report&quot;)"><h2>{{t("Delete this report?")}}</h2><p class="delete-target">{{deleteTarget?.name}}</p><p class="muted">{{t("The saved analysis and its conversation will be permanently removed. You can upload the PDF again later.")}}</p><div class="confirm-actions"><button class="outline-button" :disabled="deleteBusy" @click="deleteConfirm=false">{{t("Keep report")}}</button><button class="danger-button" :disabled="deleteBusy||!canDeleteReport" @click="deleteReport">{{deleteBusy?t('Deleting…'):t('Delete report')}}</button></div></section></div>

 </div>
</template>
