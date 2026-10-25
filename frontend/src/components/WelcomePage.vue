<script setup>
import {onMounted,onUnmounted,ref} from 'vue';
import {ArrowUpRight,ArrowDown,ArrowRight,Check,FileText,MessageCircle} from 'lucide-vue-next';
const emit=defineEmits(['signin','sample']);
const root=ref(null);let observer;
onMounted(()=>{
 if(!('IntersectionObserver' in window)||window.matchMedia('(prefers-reduced-motion: reduce)').matches)return;
 observer=new IntersectionObserver(entries=>{for(const entry of entries)if(entry.isIntersecting){entry.target.classList.remove('reveal-pending');observer.unobserve(entry.target);}},{threshold:0.12});
 root.value.querySelectorAll('[data-reveal]').forEach(el=>{el.classList.add('reveal-pending');observer.observe(el);});
});
onUnmounted(()=>observer?.disconnect());
</script>
<template>
 <main ref="root" class="welcome">
  <section class="welcome-hero">
   <p class="welcome-eyebrow">LESS GUESSWORK. BETTER SEMESTERS.</p>
   <h1>Your degree.<br>Your questions.<br><span>A clearer plan.</span></h1>
   <p class="welcome-intro">Turn your LionPATH report into a conversation.<br>Find courses, explore minors, and see what’s left to graduate.</p>
   <div class="welcome-actions"><button class="welcome-primary" @click="emit('signin')">Find my next step <ArrowUpRight :size="20"/></button><button class="welcome-secondary" @click="emit('sample')">Explore a sample <ArrowRight :size="18"/></button></div>
   <a class="welcome-scroll" href="#course-discovery" @click.prevent="root.querySelector('#course-discovery').scrollIntoView({behavior:'auto'})">A few questions we can help with <ArrowDown :size="16"/></a>
  </section>
  <section id="course-discovery" class="welcome-feature" data-reveal>
   <div class="welcome-copy"><p class="welcome-eyebrow">01 / FIND YOUR FIT</p><h2>Want to find<br><span>easy-A courses?</span></h2><p>Start with courses that actually count toward your degree. Ask about workload, prerequisites, and student experiences to find a better fit for your semester.</p><p class="welcome-fine">Student reviews offer perspective, not a guaranteed grade.</p><button class="welcome-text-link" @click="emit('signin')">Find a course that fits <ArrowUpRight :size="18"/></button></div>
   <div class="welcome-preview course-preview" aria-label="Illustrative course planning conversation"><div class="preview-label"><MessageCircle :size="16"/> A CONVERSATION, NOT ANOTHER SPREADSHEET <span>Example</span></div><div class="preview-question">What’s a manageable elective that counts toward my degree?</div><div class="preview-answer"><span class="preview-spark">✧</span><div><strong>Let’s narrow it down.</strong><p>Start with your open requirements, then compare the options.</p></div></div><div class="preview-checks"><div><Check :size="17"/> Does it satisfy a requirement?</div><div><Check :size="17"/> Are the prerequisites covered?</div><div><Check :size="17"/> What do students say about the workload?</div></div><div class="preview-caption">Your report + catalog references + web sources</div></div>
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
.welcome-eyebrow{font-size:11px;font-weight:650;letter-spacing:.17em;line-height:1.8;margin:0 0 26px;color:var(--muted)}
.welcome h1{font-size:clamp(52px,6.8vw,88px);font-weight:550;line-height:1.04;letter-spacing:-.065em;margin:0}
.welcome h1 span,.welcome h2 span{color:var(--psu-blue)}
.welcome-intro{font-size:18px;line-height:1.8;color:var(--muted);margin:28px 0 30px}
.welcome-actions{display:flex;gap:22px;align-items:center;justify-content:center;flex-wrap:wrap}
.welcome-primary{display:inline-flex;gap:22px;align-items:center;background:var(--psu-navy);color:white;border-radius:9px;padding:16px 22px;font-size:16px;min-height:54px}
.welcome-primary:hover{background:#1e407c;transform:translateY(-2px)}
.welcome-secondary,.welcome-text-link{display:inline-flex;gap:12px;align-items:center;padding:12px 0;font-size:15px;font-weight:550}
.welcome-secondary:hover,.welcome-text-link:hover{text-decoration:underline;text-underline-offset:5px}
.welcome-scroll{display:flex;align-items:center;gap:12px;margin-top:68px;color:var(--muted);font-size:12px;text-decoration:none}
.welcome-feature{display:grid;grid-template-columns:1fr 1fr;gap:70px;align-items:center;min-height:620px;padding:95px 0;border-top:1px solid var(--line);scroll-margin-top:40px}
.welcome h2{font-size:clamp(36px,4.6vw,58px);font-weight:500;line-height:1.12;letter-spacing:-.045em;margin:0 0 24px}
.welcome-copy>p:not(.welcome-eyebrow){max-width:410px;font-size:17px;line-height:1.85;color:var(--muted)}
.welcome-copy>p.welcome-fine{font-size:12px;line-height:1.6;margin-top:18px}
.welcome-feature-reverse .welcome-copy{order:2}.welcome-feature-reverse .welcome-preview{order:1}
.welcome-preview{border:1px solid var(--line);background:var(--surface);border-radius:20px;padding:28px;box-shadow:0 18px 60px #001e4408;min-width:0}
.preview-label{display:flex;align-items:center;gap:8px;font-size:9px;letter-spacing:.07em;font-weight:600;line-height:1.6;color:var(--muted)}
.preview-label>span{margin-left:auto;letter-spacing:0;font-weight:400;border:1px solid var(--line);border-radius:4px;padding:2px 6px;white-space:nowrap}
.preview-question{margin:30px 0 28px 24px;padding:16px 19px;background:var(--surface-subtle);border-radius:14px 14px 3px 14px;font-size:15px;line-height:1.7}
.preview-answer{display:flex;gap:15px;font-size:15px;line-height:1.7}.preview-answer strong{font-weight:550}.preview-answer p{color:var(--muted);margin:6px 0}.preview-spark{font-size:26px;color:var(--psu-blue)}
.preview-checks{padding:10px 0 14px 35px}.preview-checks>div{display:flex;gap:10px;align-items:center;padding:9px 0;font-size:12px;line-height:1.5}.preview-checks svg{color:var(--psu-blue);flex-shrink:0}
.preview-caption{border-top:1px solid var(--line);padding-top:18px;margin-top:18px;font-size:11px;color:var(--muted);line-height:1.8}
.minor-orbits{position:relative;display:flex;justify-content:center;align-items:center;min-height:250px;padding:28px 0}
.minor-orbit{width:180px;height:180px;border-radius:50%;border:1px solid #96bee6;background:#96bee617;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center;font-size:13px;line-height:1.7;flex-shrink:0}
.minor-orbit strong{font-size:24px;font-weight:500}.second-orbit{margin-left:-42px;background:#96bee633;border-style:dashed}
.overlap-label{display:flex;gap:8px;align-items:center;justify-content:center;font-size:12px;color:var(--muted)}.overlap-label span{width:7px;height:7px;border-radius:50%;background:var(--psu-blue)}
.preview-credit{display:flex;gap:12px;align-items:baseline;margin:42px 0 26px}.preview-credit strong{font-size:62px;font-weight:500;letter-spacing:-.06em}.preview-credit span{font-size:13px;color:var(--muted)}
.preview-progress{display:flex;height:16px;border-radius:5px;overflow:hidden;background:var(--surface-subtle)}.preview-progress span:first-child{width:60%;background:var(--psu-blue)}.preview-progress span:last-child{width:12.5%;background:#96bee6}
.preview-legend{display:flex;justify-content:space-between;gap:10px;margin:24px 0 32px}.preview-legend>div{font-size:11px;color:var(--muted)}.preview-legend strong{display:block;color:var(--ink);font-size:24px;font-weight:500;margin-top:10px}.preview-legend i{display:inline-block;width:6px;height:6px;border-radius:2px;background:var(--psu-blue);margin-right:5px}.preview-legend>div:nth-child(2) i{background:#96bee6}.preview-legend>div:nth-child(3) i{background:var(--line)}
.sample-requirements{border-top:1px solid var(--line);padding-top:8px}.sample-requirements>div{display:flex;justify-content:space-between;align-items:center;gap:8px;padding:12px 0;font-size:12px;border-bottom:1px solid var(--line)}.sample-status{color:var(--psu-blue);font-size:10px;background:var(--surface-subtle);padding:5px 8px;border-radius:5px;white-space:nowrap}.sample-status.planned{border:1px dashed var(--line)}.sample-status.remaining{background:transparent;color:var(--muted)}
.welcome-end{text-align:center;padding:110px 0 120px;border-top:1px solid var(--line)}.welcome-end>p:not(.welcome-eyebrow){font-size:17px;color:var(--muted);line-height:1.8;margin-bottom:28px}.welcome-end-note{display:block;margin-top:24px;font-size:12px;color:var(--muted)}
[data-reveal]{transition:opacity .7s ease,transform .7s ease}.reveal-pending{opacity:0;transform:translateY(35px)}
@media(max-width:800px){.welcome{padding:0 24px}.welcome-hero{padding-top:65px}.welcome-feature{grid-template-columns:1fr;gap:36px;padding:72px 0;min-height:0}.welcome-feature-reverse .welcome-copy,.welcome-feature-reverse .welcome-preview{order:initial}.welcome-preview{max-width:520px;width:100%}.welcome-copy>p:not(.welcome-eyebrow){max-width:540px}.welcome-end{padding:80px 0}.welcome-scroll{margin-top:48px}}
@media(max-width:420px){.welcome{padding:0 20px}.welcome h1{font-size:51px}.welcome-intro{font-size:16px}.welcome-actions{gap:10px}.welcome-preview{padding:20px}.minor-orbit{width:145px;height:145px}.minor-orbits{min-height:210px}.preview-label{font-size:8px}.preview-checks{padding-left:0}}
@media(prefers-reduced-motion:reduce){[data-reveal]{transition:none;transform:none;opacity:1}.welcome-primary:hover{transform:none}}
</style>
