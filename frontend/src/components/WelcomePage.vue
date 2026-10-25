<script setup>
import {onMounted,onUnmounted,ref} from 'vue';
import {ArrowUpRight,ArrowDown,ArrowRight,Check,FileText,MessageCircle,Sparkles} from 'lucide-vue-next';
const emit=defineEmits(['signin','sample']);
const root=ref(null);let observer;
onMounted(()=>{
 if(!('IntersectionObserver' in window)||window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;
 observer=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){entry.target.classList.remove('reveal-pending');observer.unobserve(entry.target);}},{root:root.value,threshold:0.12});
 root.value.querySelectorAll('[data-reveal]').forEach(el=>{el.classList.add('reveal-pending');observer.observe(el);});
});
onUnmounted(()=>observer?.disconnect());
</script>
<template>
 <main ref="root" class="welcome" tabindex="0" aria-label="About OnlyPlans">
  <section class="welcome-hero">
   <p class="welcome-eyebrow">LESS GUESSWORK. BETTER SEMESTERS.</p>
   <h1>Your <span>only</span> plan<br>for what’s next.</h1>
   <p class="welcome-intro">Turn your LionPATH report into a conversation.<br>Find courses, explore minors, and see what’s left to graduate.</p>
   <div class="welcome-actions"><button class="welcome-primary" @click="emit('signin')">Find my next step <ArrowUpRight :size="20"/></button><button class="welcome-secondary" @click="emit('sample')">Explore a sample <ArrowRight :size="18"/></button></div>
   <a class="welcome-scroll" href="#course-discovery" @click.prevent="root.querySelector('#course-discovery').scrollIntoView({block:'start'})">A few questions we can help with <ArrowDown :size="16"/></a>
  </section>
  <section id="course-discovery" class="welcome-feature" data-reveal>
   <div class="welcome-copy"><p class="welcome-eyebrow">01 / FIND YOUR FIT</p><h2>Want to find<br><span>easy-A courses?</span></h2><p>Start with courses that actually count toward your degree. Ask about workload, prerequisites, and student experiences to find a better fit for your semester.</p><p class="welcome-fine">Student reviews offer perspective, not a guaranteed grade.</p><button class="welcome-text-link" @click="emit('signin')">Find a course that fits <ArrowUpRight :size="18"/></button></div>
   <div class="welcome-preview course-preview" aria-label="Illustrative course planning conversation"><div class="preview-label"><MessageCircle :size="16"/> A CONVERSATION, NOT ANOTHER SPREADSHEET <span>Example</span></div><div class="preview-question">What’s a manageable elective that counts toward my degree?</div><div class="preview-answer"><span class="preview-spark"><Sparkles :size="18"/></span><div><strong>Let’s narrow it down.</strong><p>Start with your open requirements, then compare the options.</p></div></div><div class="preview-checks"><div><Check :size="17"/> Does it satisfy a requirement?</div><div><Check :size="17"/> Are the prerequisites covered?</div><div><Check :size="17"/> What do students say about the workload?</div></div><div class="preview-caption">Your report + catalog references + web sources</div></div>
  </section>
  <section class="welcome-feature welcome-feature-reverse" data-reveal>
   <div class="welcome-copy"><p class="welcome-eyebrow">02 / CONNECT THE DOTS</p><h2>Which minor<br><span>fits your major?</span></h2><p>You may be closer than you think. Explore how your completed and planned courses overlap with a minor, and ask what you would need next.</p><button class="welcome-text-link" @click="emit('signin')">Explore the possibilities <ArrowUpRight :size="18"/></button></div>
   <div class="welcome-preview minor-preview"><div class="preview-label">ONE BIGGER PICTURE <span>Illustration</span></div><div class="minor-orbits"><div class="minor-orbit major-orbit">Your<br><strong>major</strong></div><div class="minor-orbit second-orbit">A possible<br><strong>minor</strong></div></div><div class="overlap-label"><span></span> Discover where your courses overlap</div><p class="preview-caption">See the overlap. Understand the extra requirements.<br>Make an informed choice.</p></div>
  </section>
  <section class="welcome-feature" data-reveal>
   <div class="welcome-copy"><p class="welcome-eyebrow">03 / KNOW WHAT’S NEXT</p><h2>Less PDF.<br><span>More clarity.</span></h2><p>See completed credits, planned courses, and remaining requirements in one place. Ask what a confusing requirement means—and which courses could satisfy it.</p><button class="welcome-text-link" @click="emit('sample',true)">See a sample report <ArrowUpRight :size="18"/></button></div>
   <div class="welcome-preview degree-preview"><div class="preview-label"><FileText :size="16"/> YOUR DEGREE, AT A GLANCE <span>Sample</span></div><div class="preview-credit"><strong>72</strong><span>/ 120 credits completed</span></div><div class="preview-progress" role="img" aria-label="Sample: 72 completed, 15 planned, 33 remaining credits"><span></span><span></span></div><div class="preview-legend"><div><i></i>Completed<strong>72</strong></div><div><i></i>Planned<strong>15</strong></div><div><i></i>Remaining<strong>33</strong></div></div><div class="sample-requirements"><div><span>Writing &amp; communication</span><span class="sample-status">Completed</span></div><div><span>Major core</span><span class="sample-status planned">In progress</span></div><div><span>Electives</span><span class="sample-status remaining">To complete</span></div></div><p class="preview-caption">Illustrative requirements, not a personal degree audit.</p><button class="welcome-text-link" @click="emit('sample',true)">Open the sample analysis <ArrowUpRight :size="18"/></button></div>
  </section>
  <section class="welcome-end" data-reveal><p class="welcome-eyebrow">FROM “WHAT NOW?” TO “WHAT’S NEXT.”</p><h2>Try OnlyPlans.<br><span>Your next semester starts here.</span></h2><p>Sign in. Add your LionPATH PDF in Profile.<br>Then ask your first question.</p><button class="welcome-primary" @click="emit('signin')">Try OnlyPlans <ArrowUpRight :size="20"/></button><span class="welcome-end-note">Your report stays in your profile, ready for your next conversation.</span></section>
 </main>
</template>
<style scoped>
.welcome{max-width:1160px;margin:auto;padding:0 40px;color:var(--ink)}
.welcome-hero{text-align:center;padding:90px 0 42px;min-height:calc(100svh - 94px);display:flex;flex-direction:column;align-items:center;justify-content:center}
.welcome-eyebrow{font-size:14px;font-weight:650;letter-spacing:.12em;line-height:1.8;margin:0 0 26px;color:var(--muted)}
.welcome h1{font-family:var(--font-heading);font-size:clamp(52px,6.8vw,88px);font-weight:550;line-height:1.04;letter-spacing:-.045em;margin:0}
.welcome h1 span,.welcome h2 span{color:var(--psu-blue)}
.welcome-intro{font-size:18px;line-height:1.8;color:var(--muted);margin:28px 0 30px}
.welcome-actions{display:flex;gap:22px;align-items:center;justify-content:center;flex-wrap:wrap}
.welcome-primary{display:inline-flex;gap:14px;align-items:center;background:var(--action-bg);color:white;border-radius:999px;padding:14px 22px 14px 26px;font-size:16px;min-height:52px;box-shadow:0 10px 30px #001e4426}
.welcome-primary:hover{background:var(--action-hover);transform:translateY(-2px)}
.welcome-secondary,.welcome-text-link{display:inline-flex;gap:10px;align-items:center;font-size:16px;font-weight:550}.welcome-secondary{border-radius:999px;padding:14px 22px;min-height:52px;background:var(--surface);box-shadow:var(--card-shadow)}.welcome-text-link{padding:12px 0;color:var(--psu-blue)}
.welcome-secondary:hover{background:var(--accent-soft)}.welcome-text-link:hover{text-decoration:underline;text-underline-offset:5px}
.welcome-scroll{display:flex;align-items:center;gap:12px;margin-top:68px;color:var(--muted);font-size:15px;text-decoration:none}
.welcome-feature{display:grid;grid-template-columns:1fr 1fr;gap:70px;align-items:center;min-height:620px;padding:95px 0;scroll-margin-top:40px}
.welcome h2{font-family:var(--font-heading);font-size:clamp(36px,4.6vw,58px);font-weight:500;line-height:1.12;letter-spacing:-.045em;margin:0 0 24px}
.welcome-copy>p:not(.welcome-eyebrow){max-width:410px;font-size:18px;line-height:1.85;color:var(--muted)}
.welcome-copy>p.welcome-fine{font-size:15px;line-height:1.6;margin-top:18px}
.welcome-feature-reverse .welcome-copy{order:2}.welcome-feature-reverse .welcome-preview{order:1}
.welcome-preview{background:var(--surface);border-radius:28px;padding:30px;box-shadow:var(--card-shadow);min-width:0}
.preview-label{display:flex;flex-wrap:wrap;align-items:center;gap:8px;font-size:14px;letter-spacing:.07em;font-weight:600;line-height:1.6;color:var(--muted)}
.preview-label>svg{flex-shrink:0}.preview-label>span{margin-left:auto;letter-spacing:0;font-weight:500;border-radius:999px;padding:3px 10px;background:var(--accent-soft);color:var(--psu-blue);white-space:nowrap}
.preview-question{margin:30px 0 28px 24px;padding:14px 18px;background:var(--action-bg);color:#fff;border-radius:18px 18px 6px 18px;font-size:16px;line-height:1.7}
.preview-answer{display:flex;gap:15px;font-size:16px;line-height:1.7}.preview-answer strong{font-weight:550}.preview-answer p{color:var(--muted);margin:6px 0}.preview-spark{display:grid;place-items:center;flex-shrink:0;width:36px;height:36px;border-radius:12px;background:var(--accent-soft);color:var(--psu-blue)}
.preview-checks{padding:10px 0 14px 51px}.preview-checks>div{display:flex;gap:10px;align-items:center;padding:9px 0;font-size:15px;line-height:1.5}.preview-checks svg{color:var(--psu-blue);flex-shrink:0;box-sizing:content-box;padding:4px;border-radius:50%;background:var(--accent-soft)}
.preview-caption{border-top:1px solid var(--surface-subtle);padding-top:18px;margin-top:18px;font-size:14px;color:var(--muted);line-height:1.8}
.minor-orbits{position:relative;display:flex;justify-content:center;align-items:center;min-height:250px;padding:28px 0}
.minor-orbit{width:180px;height:180px;border-radius:50%;border:1px solid #96bee6;background:#96bee617;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center;font-size:16px;line-height:1.7;flex-shrink:0}
.minor-orbit strong{font-size:24px;font-weight:500}.second-orbit{margin-left:-42px;background:#96bee633;border-style:dashed}
.overlap-label{display:flex;gap:8px;align-items:center;justify-content:center;font-size:15px;color:var(--muted)}.overlap-label span{width:7px;height:7px;border-radius:50%;background:var(--psu-blue)}
.preview-credit{display:flex;gap:12px;align-items:baseline;margin:42px 0 26px}.preview-credit strong{font-size:62px;font-weight:500;letter-spacing:-.06em}.preview-credit span{font-size:16px;color:var(--muted)}
.preview-progress{display:flex;height:14px;border-radius:999px;overflow:hidden;background:var(--surface-subtle)}.preview-progress span:first-child{width:60%;background:var(--psu-blue)}.preview-progress span:last-child{width:12.5%;background:#96bee6}
.preview-legend{display:flex;flex-wrap:wrap;justify-content:space-between;gap:10px;margin:24px 0 32px}.preview-legend>div{font-size:14px;color:var(--muted)}.preview-legend strong{display:block;color:var(--ink);font-size:24px;font-weight:500;margin-top:10px}.preview-legend i{display:inline-block;width:6px;height:6px;border-radius:2px;background:var(--psu-blue);margin-right:5px}.preview-legend>div:nth-child(2) i{background:#96bee6}.preview-legend>div:nth-child(3) i{background:var(--line)}
.sample-requirements{padding-top:8px}.sample-requirements>div{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:12px 0;font-size:15px;border-bottom:1px solid var(--surface-subtle)}.sample-status{color:var(--psu-blue);font-size:14px;background:var(--accent-soft);padding:5px 10px;border-radius:999px;white-space:nowrap}.sample-status.planned{background:#96bee633}.sample-status.remaining{background:var(--surface-subtle);color:var(--muted)}
.welcome-end{text-align:center;padding:90px 32px 96px;margin:40px 0 80px;background:transparent;border:0;border-radius:0;box-shadow:none}.welcome-end>p:not(.welcome-eyebrow){font-size:18px;color:var(--muted);line-height:1.8;margin-bottom:28px}.welcome-end-note{display:block;margin-top:24px;font-size:15px;color:var(--muted)}
.welcome{--ease-out:cubic-bezier(.2,.7,.2,1)}
.welcome-hero>*{animation:welcome-rise .9s var(--ease-out) both}.welcome-hero>:nth-child(2){animation-delay:.08s}.welcome-hero>:nth-child(3){animation-delay:.18s}.welcome-hero>:nth-child(4){animation-delay:.28s}.welcome-hero>:nth-child(5){animation-delay:.5s}
@keyframes welcome-rise{from{opacity:0;transform:translateY(22px)}}
.welcome-scroll svg{animation:welcome-nudge 1.8s ease-in-out 1.4s infinite}
@keyframes welcome-nudge{50%{transform:translateY(4px)}}
.welcome-copy>*,.welcome-preview,.welcome-end,.welcome-end>*{transition:opacity .8s var(--ease-out),transform .8s var(--ease-out)}
.welcome-copy>:nth-child(2){transition-delay:.07s}.welcome-copy>:nth-child(3){transition-delay:.14s}.welcome-copy>:nth-child(4){transition-delay:.21s}.welcome-copy>:nth-child(5){transition-delay:.28s}
.welcome-preview{transition-delay:.12s}
.reveal-pending .welcome-copy>*{opacity:0;transform:translateY(26px)}
.reveal-pending .welcome-preview{opacity:0;transform:translateX(44px) scale(.96)}
.reveal-pending.welcome-feature-reverse .welcome-preview{transform:translateX(-44px) scale(.96)}
.minor-orbit{transition:transform 1s var(--ease-out) .45s}.reveal-pending .major-orbit{transform:translateX(-34px)}.reveal-pending .second-orbit{transform:translateX(34px)}
.preview-progress span{transition:width 1.1s var(--ease-out) .45s}.reveal-pending .preview-progress span:first-child,.reveal-pending .preview-progress span:last-child{width:0}
.welcome-end>:nth-child(2){transition-delay:.1s}.welcome-end>:nth-child(3){transition-delay:.18s}.welcome-end>:nth-child(4){transition-delay:.26s}.welcome-end>:nth-child(5){transition-delay:.34s}
.reveal-pending.welcome-end{opacity:0;transform:translateY(30px) scale(.98)}.reveal-pending.welcome-end>*{opacity:0;transform:translateY(18px)}
@media(max-width:800px){.welcome{padding:0 24px}.welcome-hero{padding-top:65px}.welcome-feature{grid-template-columns:1fr;gap:36px;padding:72px 0;min-height:0}.welcome-feature-reverse .welcome-copy,.welcome-feature-reverse .welcome-preview{order:initial}.welcome-preview{max-width:520px;width:100%}.welcome-copy>p:not(.welcome-eyebrow){max-width:540px}.welcome-end{padding:64px 24px;margin-bottom:48px}.welcome-scroll{margin-top:48px}}
@media(max-width:420px){.welcome{padding:0 20px}.welcome h1{font-family:var(--font-heading);font-size:51px}.welcome-intro{font-size:18px}.welcome-actions{gap:10px}.welcome-preview{padding:20px}.minor-orbit{width:145px;height:145px}.minor-orbits{min-height:210px}.preview-label{font-size:14px}.preview-checks{padding-left:0}}
/* Keep each section within the space between the fixed header and footer.
   Sections may grow taller so small screens and zoom never clip content. */
.welcome{--section-height:calc(100dvh - var(--app-header-height,94px) - var(--app-footer-height,72px));width:100%;height:var(--section-height);flex:none;overflow-y:auto;overflow-x:hidden;overscroll-behavior-y:contain;scroll-snap-type:y mandatory;scroll-behavior:smooth;scrollbar-width:none}
.welcome::-webkit-scrollbar{display:none}
.welcome>section{min-height:var(--section-height);scroll-snap-align:start;scroll-snap-stop:always;scroll-margin-top:0}
.welcome-hero{padding:40px 0}
.welcome-feature{padding:40px 0}
.welcome-end{margin:0;padding:40px 24px;display:flex;flex-direction:column;align-items:center;justify-content:center}
@media(max-width:800px){.welcome-feature{padding:36px 0}.welcome-end{padding:40px 24px}}
@media(prefers-reduced-motion:reduce){.welcome{scroll-behavior:auto}.welcome *,.welcome-end{animation:none!important;transition:none!important}.welcome-primary:hover{transform:none}}
</style>
