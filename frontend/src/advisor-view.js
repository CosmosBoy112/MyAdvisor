// Display helpers for the screen the advisor agent opens. The data is server-built (AdvisorViews.java); nothing here recomputes it.
export const VIEW_TITLES={course_detail:'Course details',course_options:'Course options',requirement_gap:'What is left',semester_plan:'Suggested semester plan',minor_check:'Math minor check'};
export const PREREQ_LABELS={met:'Prerequisites met',in_progress:'Met once current courses are passed',not_met:'Prerequisites not met',unknown:'Could not be verified',no_prerequisites_in_database:'No prerequisites listed'};
const SEASONS={FA:'Fall',SP:'Spring',SU:'Summer'};
export function viewTitle(view){return VIEW_TITLES[view?.type]||'Advisor view';}
export function seasonLabel(code){return SEASONS[code]||code||'';}
/** Report status of a course: 'no', 'in_progress' or 'recorded:GRADE' (also 'generic' / 'not_in_report' on plan rows). */
export function reportStatus(value){
 if(!value||value==='no'||value==='not_in_report')return {key:'missing',label:'Not in your report'};
 if(value==='in_progress')return {key:'progress',label:'In progress'};
 if(value==='generic')return {key:'generic',label:'Choose a course'};
 if(value.startsWith('recorded:'))return {key:'done',label:'In your report',grade:value.slice(9)};
 return {key:'missing',label:value};
}
/** Prerequisite tree → rows to show: each ALL/ANY group with its course leaves, so "one of" choices stay visible. */
export function prerequisiteRows(tree,depth=0,rows=[]){
 if(!tree)return rows;
 if(tree.type==='ALL'||tree.type==='ANY'){
  rows.push({depth,group:tree.type,result:tree.result});
  for(const child of tree.children||[])prerequisiteRows(child,depth+1,rows);
 }else rows.push({depth,course:tree.course,result:tree.result,reason:tree.reason,text:tree.unparsed_text,grade:tree.matched_record?.grade,minGrade:tree.min_grade});
 return rows;
}
export function planProgress(plan){
 let done=0,progress=0,total=0;
 for(const term of plan?.terms||[])for(const course of term.courses||[]){
  if(course.inReport==='generic')continue;total++;
  if(String(course.inReport).startsWith('recorded:'))done++;else if(course.inReport==='in_progress')progress++;
 }
 return {done,progress,total};
}
export function officialUrl(value){try{const u=new URL(value);return u.protocol==='https:'&&(u.hostname==='psu.edu'||u.hostname.endsWith('.psu.edu'))?u.href:null;}catch{return null;}}
// Live progress while an answer is being written (polled from /chat/progress). Labels describe the step, never its data.
const TOOL_LABELS={get_student_record:'Reading your report…',search_courses:'Searching the course catalog…',get_course:'Reading course details…',check_prerequisites:'Checking prerequisites…',get_program_requirements:'Reviewing your program requirements…',find_courses_for_requirement:'Finding courses for a requirement…',get_policies:'Reading program policies…',get_minor_assessment:'Comparing the Math minor…',web_search:'Searching the web…',show_view:'Preparing a screen…',save_memory:'Saving to memory…',forget_memory:'Updating memory…'};
export function progressLabel(step){
 if(step?.kind==='tool')return TOOL_LABELS[step.detail]||'Looking something up…';
 if(step?.kind==='fallback')return 'Switching to the backup model…';
 if(step?.kind==='llm')return step.detail==='answer'?'Writing the answer…':'Thinking about your question…';
 return 'Thinking about your question…';
}
/** The current step and the distinct steps already finished, oldest first (repeats collapsed). */
export function progressView(steps=[]){
 const labels=[];for(const s of steps){const l=progressLabel(s);if(labels.at(-1)!==l)labels.push(l);}
 if(!labels.length)return {current:'Thinking about your question…',done:[]};
 return {current:labels.at(-1),done:[...new Set(labels.slice(0,-1).filter(l=>l!==labels.at(-1)&&l!=='Thinking about your question…'&&l!=='Writing the answer…'))]};
}
