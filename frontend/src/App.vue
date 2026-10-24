<script setup>
import {computed,inject,nextTick,ref,watch,onMounted,onUnmounted} from 'vue';
import {useAuth0} from '@auth0/auth0-vue';
import {ArrowUp,ArrowUpRight,ArrowRight,Plus,X,FileText,Check,ChevronDown,ChevronRight,BookOpen,GraduationCap,Compass,MessageCircle,ShieldCheck,LoaderCircle,LogOut,Download,Info,Search,RotateCcw,Trash2,Sun,Moon} from 'lucide-vue-next';
import sample from './sample.json';
import {apiRequest} from './api.js';
import {sessionStatus} from './auth-config.js';
import {progress,filterRequirements,validateFile} from './model.js';
const theme=ref(document.documentElement.dataset.theme||'light');
function toggleTheme(){
 theme.value=theme.value==='dark'?'light':'dark';
 document.documentElement.dataset.theme=theme.value;
 document.querySelector('meta[name="theme-color"]')?.setAttribute('content',theme.value==='dark'?'#091525':'#ffffff');
 try{localStorage.setItem('lionpathway-theme',theme.value);}catch{}
}
const configured=inject('authConfigured');
const auth=configured?useAuth0():null;
const report=ref(null),intent=ref(''),file=ref(null),fileInput=ref(null),busy=ref(false),error=ref(''),notice=ref(''),dragging=ref(false),filter=ref('all'),expanded=ref(null),source=ref(null),chatOpen=ref(false),question=ref(''),messages=ref([]),chatBusy=ref(false),chatBody=ref(null),consent=ref(false);
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
async function openReport(id){reportBusy.value=true;error.value='';try{const next=await request(`/api/reports/${id}`);const history=await request(`/api/reports/${id}/messages`);report.value=next;messages.value=history.map(m=>({role:m.role,text:m.content}));historyOpen.value=false;filter.value='all';expanded.value=null;source.value=null;chatOpen.value=false;window.scrollTo(0,0);}catch(e){error.value=e.message;}finally{reportBusy.value=false;}}
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
const completedCount=computed(()=>report.value?.requirements.filter(x=>x.status==='complete').length||0);
const statuses={complete:'Complete',in_progress:'In progress',remaining:'Remaining',review:'Needs review'};
const greeting=computed(()=>auth?.user.value?.given_name||'there');
async function headers(){if(!configured||!auth.isAuthenticated.value)throw Error('Sign in to analyze your personal report.');return {Authorization:`Bearer ${await auth.getAccessTokenSilently()}`};}
function request(path,options={},settings={}){return apiRequest(base+path,options,{getHeaders:headers,...settings});}
function pick(f){error.value='';const problem=validateFile(f);if(problem){error.value=problem;return;}file.value=f;}
function drop(event){dragging.value=false;if(event.dataTransfer.files.length)pick(event.dataTransfer.files[0]);}
async function signIn(){error.value='';if(!configured){notice.value='Live sign-in needs Auth0 configuration. You can explore the sample without an account.';return;}try{if(intent.value.trim())sessionStorage.setItem('lionpathway-goal',intent.value);await auth.loginWithRedirect();}catch(e){error.value=e.message;}}
function signOut(){auth?.logout({logoutParams:{returnTo:window.location.origin}});}
function demo(){report.value=structuredClone(sample);messages.value=[];filter.value='all';error.value='';notice.value='';window.scrollTo(0,0);}
function reset(){report.value=null;messages.value=[];chatOpen.value=false;source.value=null;error.value='';expanded.value=null;deleteConfirm.value=false;}
async function analyze(){error.value='';if(authLoading.value){error.value='Sign-in is still loading. Please try again in a moment.';return;}if(!file.value){fileInput.value.click();return;}if(!configured){error.value='Live PDF analysis requires Auth0, Gemini, and the backend. Use “Explore a sample” to preview the experience.';return;}if(!auth.isAuthenticated.value){await signIn();return;}if(!consent.value){error.value='Please allow your report text to be sent to the AI providers for analysis.';return;}if(busy.value)return;busy.value=true;elapsed.value=0;uploadStage.value='auth';uploadController=new AbortController();const started=Date.now();const ticker=setInterval(()=>elapsed.value=Math.floor((Date.now()-started)/1000),1000);try{const form=new FormData();form.append('file',file.value);form.append('intent',intent.value);report.value=await request('/api/reports',{method:'POST',body:form},{signal:uploadController.signal,onStage:stage=>uploadStage.value=stage});messages.value=[];filter.value='all';expanded.value=null;file.value=null;consent.value=false;void loadHistory();window.scrollTo(0,0);}catch(e){error.value=e.message;}finally{clearInterval(ticker);uploadController=null;busy.value=false;}}
function askAbout(item){chatOpen.value=true;question.value=`What does “${item.title}” mean, and what is left to complete?`;}
function demoAnswer(q){let item=report.value.requirements.find(r=>q.toLowerCase().includes(r.title.toLowerCase()));if(!item&&/minor|math/i.test(q))item=report.value.requirements.find(r=>r.id==='minor');if(item)return `${item.description}\n\nSample source: page ${item.evidence.page}. This is an illustrative answer, not a live Gemini response.`;if(/credit|left|remaining/i.test(q))return 'In this sample, 72 of 120 credits are completed, 15 are in progress, and 33 remain beyond current courses. Finishing the credit total does not automatically satisfy every requirement.\n\nThis is an illustrative answer, not a live Gemini response.';return 'This is sample mode. Try asking about Department List, Supportive Electives, the Mathematics minor, or remaining credits. Live Gemini answers become available when the services are configured.';}
async function send(){const q=question.value.trim();if(!q||chatBusy.value)return;question.value='';messages.value.push({role:'user',text:q});chatBusy.value=true;try{const answer=report.value.demo?demoAnswer(q):(await request(`/api/reports/${report.value.id}/chat`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({question:q})})).answer;messages.value.push({role:'assistant',text:answer});}catch(e){messages.value.push({role:'error',text:e.message});}finally{chatBusy.value=false;await nextTick();chatBody.value?.scrollTo({top:chatBody.value.scrollHeight,behavior:'smooth'});}}
function download(){const blob=new Blob([JSON.stringify(report.value,null,2)],{type:'application/json'});const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='lionpathway-analysis.json';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function value(n){return Number.isFinite(n)?n:'—';}
</script>

<template>
 <div class="app-shell" @keydown.esc="source=null;chatOpen=false;historyOpen=false;deleteConfirm=false">
  <header class="topbar">
   <button class="brand" @click="reset" aria-label="LionPathway home"><span class="brand-icon"><GraduationCap :size="23"/></span><span>Lion<span class="brand-light">Pathway</span><span class="brand-dot">.</span></span></button>
   <div class="header-right"><button type="button" class="theme-toggle" :aria-label="theme==='dark'?'Switch to light mode':'Switch to dark mode'" :title="theme==='dark'?'Switch to light mode':'Switch to dark mode'" @click="toggleTheme"><Sun v-if="theme==='dark'" :size="18"/><Moon v-else :size="18"/></button><span class="independent">Built for Penn State students</span><span class="header-divider"></span><span v-if="session==='restoring'" class="session-restoring" role="status"><LoaderCircle :size="14" class="spin"/> Checking session…</span><button v-if="session==='signed-in'" class="sign-in" @click="historyOpen=true;loadHistory()"><BookOpen :size="15"/> My reports</button><button v-if="session==='signed-out'" class="sign-in" @click="signIn">Sign in <ArrowUpRight :size="15"/></button><button v-else-if="session==='signed-in'" class="sign-in" @click="signOut()">Sign out <LogOut :size="15"/></button></div>
  </header>
  <div v-if="deletionNotice" class="success-toast" role="status" aria-live="polite" aria-atomic="true"><Check :size="18"/><span>{{deletionNotice}}</span><button type="button" @click="dismissDeletionNotice" aria-label="Dismiss deletion notification"><X :size="16"/></button></div>
  <div v-if="notice" class="notice" role="status">{{notice}}<button @click="notice=''" aria-label="Dismiss"><X :size="16"/></button></div>
  <div v-if="error && (report || historyOpen)" class="global-error error" role="alert">{{error}}<button @click="error=''" aria-label="Dismiss error"><X :size="16"/></button></div>
  <main v-if="!report" class="landing">
   <div class="hero-decoration" aria-hidden="true"><span></span><span></span><span></span></div>
   <section class="hero">
    <div class="eyebrow"><span class="tiny-dot"></span> LESS GUESSWORK. MORE POSSIBILITY.</div>
    <h1>Your next chapter,<br><span>clearer.</span></h1>
    <p class="hero-description">Your degree report has the details.<br>Let’s turn them into a direction.</p>
    <form class="search-card" :class="{dragging}" @submit.prevent="analyze" @dragover.prevent="dragging=true" @dragleave.prevent="dragging=false" @drop.prevent="drop">
     <label class="sr-only" for="intent">Your major and the minor you are considering</label>
     <textarea id="intent" @keydown="submitOnEnter" title="Enter to analyze · Shift+Enter for a new line" v-model="intent" maxlength="1000" placeholder="I’m majoring in Computer Science, considering a Math minor…" :disabled="busy" rows="2"></textarea>
     <div v-if="file" class="file-chip"><FileText :size="15"/><span>{{file.name}}</span><button type="button" @click="file=null" aria-label="Remove PDF"><X :size="14"/></button></div>
     <div class="search-toolbar"><button type="button" class="attach-button" @click="fileInput.click()" :disabled="busy"><Plus :size="18"/> Attach report <span>PDF</span></button><button type="submit" class="submit-button" :disabled="busy" aria-label="Analyze report"><LoaderCircle v-if="busy" class="spin" :size="21"/><ArrowUp v-else :size="22"/></button></div>
     <input ref="fileInput" type="file" accept=".pdf,application/pdf" class="sr-only" @change="e=>{if(e.target.files[0])pick(e.target.files[0]);e.target.value=''}">
    </form>
    <label v-if="file" class="consent"><input type="checkbox" v-model="consent"> Allow report text to be sent to Gemini (and Luminal for planning questions) for analysis. Remove personal identifiers before uploading.</label>
    <div v-if="file && (serviceError || (service && !service.live))" class="service-status"><Info :size="15"/><span>{{serviceError||'The server is in sample-only mode. Start it with the live profile to analyze PDFs.'}}</span><button type="button" @click="checkService">Retry</button></div>
    <div v-if="busy" class="upload-progress"><p class="loading-message" role="status">{{uploadStage==='auth'?'Verifying your sign-in…':'Uploading and analyzing your report…'}} {{elapsed}}s</p><p v-if="uploadStage==='analysis'" class="upload-hint">AI analysis can take up to 100 seconds. You can stop waiting at any time.</p><button type="button" class="cancel-upload" @click="cancelUpload">Stop waiting</button></div>
    <p v-if="error" class="error" role="alert">{{error}}</p>
    <div class="hero-actions"><span>Start with your LionPATH What-If Report</span><span class="action-dot">·</span><button @click="demo">Explore a sample <ArrowRight :size="14"/></button></div>
    <div class="value-strip"><span><BookOpen :size="16"/> Understand your requirements</span><span><Compass :size="16"/> See what’s left</span><span><MessageCircle :size="16"/> Ask better questions</span></div>
   </section>
   <div class="landing-bottom"><span class="little-line"></span><p>A little clarity for a big decision.</p><span class="little-line"></span></div>
  </main>
  <main v-else class="dashboard">
   <div class="breadcrumb"><button @click="reset">Home</button><ChevronRight :size="13"/><span>Your degree overview</span><span v-if="report.demo" class="sample-badge">SAMPLE REPORT</span></div>
   <div class="page-heading"><div><div class="eyebrow">YOUR BIG PICTURE</div><h1>A clearer path forward<span>.</span></h1><p>{{report.major||'Major needs confirmation'}} <span>·</span> {{report.campus||'Campus not specified'}}</p></div><div class="report-heading-actions"><button v-if="!report.demo" class="outline-button delete-link" :disabled="deleteBusy" @click="confirmDelete(report)"><Trash2 :size="16"/> Delete report</button><button class="outline-button" @click="reset"><Plus :size="16"/> New report</button></div></div>
   <div v-if="report.demo" class="sample-notice"><Info :size="17"/><span>You’re exploring an illustrative report. No personal data or live AI is used.</span></div>
   <section class="overview-grid">
    <div class="progress-panel panel"><div class="panel-heading"><span>Degree progress</span><span class="subtle-pill">{{report.catalogYear||'Requirement year unknown'}}</span></div>
     <div class="credit-headline"><strong>{{value(report.totals?.completed)}}</strong><span>/ {{value(report.totals?.required)}} credits completed</span><span v-if="totals" class="percent">{{totals.percent}}%</span></div>
     <div v-if="totals" class="credit-bar" role="img" :aria-label="`${report.totals.completed} completed, ${report.totals.inProgress} in progress, ${totals.remaining} remaining credits`"><span class="bar-complete" :style="{width:totals.completeWidth+'%'}"></span><span class="bar-progress" :style="{width:totals.progressWidth+'%'}"></span></div>
     <p v-else class="unknown-note">Some credit totals need confirmation. We haven’t filled in missing values.</p>
     <div class="credit-legend"><div><span class="legend-dot legend-complete"></span><span>Completed<strong>{{value(report.totals?.completed)}}</strong></span></div><div><span class="legend-dot legend-progress"></span><span>In progress<strong>{{value(report.totals?.inProgress)}}</strong></span></div><div><span class="legend-dot gray"></span><span>Remaining<strong>{{value(totals?.remaining)}}</strong></span></div></div>
     <p class="panel-footnote">Remaining assumes current courses are completed successfully. Requirement totals may overlap.</p>
    </div>
    <div class="next-panel"><div class="compass-icon"><Compass :size="24"/></div><div class="eyebrow">A GOOD PLACE TO START</div><h2>Credits tell one part<br>of your story.</h2><p>Check each requirement below. A full credit count doesn’t always mean every box is checked.</p><button @click="filter='remaining';expanded=report.requirements.find(r=>r.status!=='complete')?.id">See what needs attention <ArrowUpRight :size="16"/></button></div>
   </section>
   <div v-if="report.warnings?.length && !report.demo" class="warnings"><p v-for="w in report.warnings" :key="w"><Info :size="15"/>{{w}}</p></div>
   <section class="requirements-section"><div class="section-heading"><div><h2>Your requirements</h2><p>{{completedCount}} of {{report.requirements.length}} listed requirements complete</p></div><div class="filters" aria-label="Filter requirements"><button v-for="(label,key) in {all:'All',remaining:'To complete',complete:'Completed'}" :key="key" :class="{active:filter===key}" @click="filter=key" :aria-pressed="filter===key">{{label}}</button></div></div>
    <div class="requirements-list"><article v-for="item in visible" :key="item.id" class="requirement" :class="{expanded:expanded===item.id}"><button class="requirement-summary" @click="expanded=expanded===item.id?null:item.id" :aria-expanded="expanded===item.id"><span class="requirement-icon" :class="item.status"><Check v-if="item.status==='complete'" :size="19"/><Info v-else-if="item.status==='review'" :size="19"/><BookOpen v-else :size="19"/></span><span class="requirement-title">{{item.title}}<span v-if="item.required!=null">{{value(item.completed)}} of {{item.required}} credits completed<span v-if="item.inProgress"> · {{item.inProgress}} in progress</span></span><span v-else>More information needed</span></span><span class="status" :class="item.status">{{statuses[item.status]||'Needs review'}}</span><ChevronDown class="expand-chevron" :size="18"/></button><div v-if="expanded===item.id" class="requirement-detail"><p>{{item.description}}</p><ul v-if="item.courses?.length"><li v-for="course in item.courses" :key="course">{{course}}</li></ul><p v-else class="muted">No eligible course list was extracted for this item.</p><div class="requirement-actions"><button v-if="item.evidence?.quote" @click="source=item"><FileText :size="14"/> {{item.evidence.verified?'View source':'Review unverified source'}} · p. {{item.evidence.page||'?'}}</button><button @click="askAbout(item)"><MessageCircle :size="14"/> Ask about this</button></div></div></article><div v-if="!visible.length" class="empty-state">No requirements in this view.</div></div>
   </section>
   <div class="report-footer"><span><FileText :size="15"/> {{report.fileName}}</span><div class="report-tools"><button @click="download"><Download :size="15"/> Export analysis</button><button v-if="!report.demo" class="delete-link" @click="confirmDelete(report)">Delete report</button></div></div>
   <button class="chat-launcher" @click="chatOpen=!chatOpen"><MessageCircle :size="19"/> Ask your report <span>✧</span></button>
  </main>
  <footer class="site-footer"><span>LionPathway <span class="footer-dot">•</span> A student-built project <span class="footer-dot">•</span> <span class="footer-team">Built by BLS</span></span><span>Independent of Penn State. Confirm academic decisions with your adviser.</span></footer>
  <div v-if="source" class="modal-backdrop" @click.self="source=null"><section class="source-modal" role="dialog" aria-modal="true" aria-label="Report evidence"><div class="panel-heading"><span><FileText :size="18"/> Report evidence</span><button @click="source=null" aria-label="Close evidence"><X :size="20"/></button></div><h2>{{source.title}}</h2><p class="muted">{{report.demo?'Illustrative source':'Extracted source'}} · Page {{source.evidence.page||'unknown'}}</p><blockquote>{{source.evidence.quote}}</blockquote><p class="panel-footnote">{{report.demo?'Illustrative sample evidence; no personal PDF was analyzed.':source.evidence.verified?'This text matches the extracted report page. This does not independently verify the academic interpretation.':'This citation could not be matched to the extracted page. Please check the original PDF.'}}</p></section></div>
  <div v-if="historyOpen" class="modal-backdrop" @click.self="historyOpen=false"><section class="source-modal history-modal" role="dialog" aria-modal="true" aria-label="Saved reports"><div class="panel-heading"><span><BookOpen :size="18"/> Your saved reports</span><button @click="historyOpen=false" aria-label="Close saved reports"><X :size="20"/></button></div><p class="muted">Reopen a report and pick up your conversation.</p><p v-if="historyBusy" class="loading-message" role="status">Loading reports…</p><p v-else-if="!savedReports.length" class="empty-state">No saved reports yet. Upload a report to get started.</p><div v-for="saved in savedReports" :key="saved.id" class="saved-report-row"><button class="saved-report" :disabled="reportBusy||deleteBusy" @click="openReport(saved.id)"><FileText :size="19"/><span><strong>{{saved.major||'Academic report'}}</strong><small>{{saved.filename}}</small><small>{{new Date(saved.createdAt).toLocaleDateString()}}</small></span><ChevronRight :size="17"/></button><button class="saved-report-delete" :disabled="deleteBusy||reportBusy" :aria-label="`Delete ${saved.filename||saved.major||'report'}`" @click="confirmDelete(saved)"><Trash2 :size="17"/><span>Delete</span></button></div></section></div>
  <div v-if="deleteConfirm" class="modal-backdrop" @click.self="!deleteBusy && (deleteConfirm=false)"><section class="source-modal" role="dialog" aria-modal="true" aria-label="Delete report"><h2>Delete this report?</h2><p class="delete-target">{{deleteTarget?.name}}</p><p class="muted">The saved analysis and its conversation will be permanently removed. You can upload the PDF again later.</p><div class="confirm-actions"><button class="outline-button" :disabled="deleteBusy" @click="deleteConfirm=false">Keep report</button><button class="danger-button" :disabled="deleteBusy" @click="deleteReport">{{deleteBusy?'Deleting…':'Delete report'}}</button></div></section></div>
  <aside v-if="chatOpen && report" class="chat-panel" role="dialog" aria-label="Ask your report"><div class="chat-header"><span class="chat-symbol">✧</span><div><strong>A little guidance</strong><span>{{report.demo?'Sample conversation':'Gemini · Luminal for planning · grounded in your report'}}</span></div><button @click="chatOpen=false" aria-label="Close chat"><X :size="20"/></button></div><div ref="chatBody" class="chat-body"><div class="assistant-message">Hi {{greeting}}. Let’s make sense of your report. What would you like to understand?</div><div v-if="!messages.length" class="suggestions"><button v-for="q in ['How many credits are left?','Explain Department List','Is my minor included?']" :key="q" @click="question=q;send()">{{q}}<ArrowUpRight :size="14"/></button></div><div v-for="(msg,i) in messages" :key="i" :class="['chat-message',msg.role]">{{msg.text}}</div><div v-if="chatBusy" class="chat-thinking" role="status"><LoaderCircle :size="16" class="spin"/> Reading your report…</div></div><form class="chat-form" @submit.prevent="send"><label for="chat-question" class="sr-only">Question about your report</label><textarea id="chat-question" v-model="question" @keydown="submitOnEnter" rows="1" maxlength="2000" title="Enter to send · Shift+Enter for a new line" placeholder="Ask about a requirement…"></textarea><button :disabled="chatBusy||!question.trim()" aria-label="Send question"><ArrowUp :size="19"/></button></form><p class="chat-disclaimer">A starting point for your next adviser conversation.</p></aside>
 </div>
</template>
