export function progress(totals) {
  const {required,completed,inProgress} = totals || {};
  if (![required,completed,inProgress].every(Number.isFinite) || required <= 0 || completed < 0 || inProgress < 0) return null;
  const completeWidth=Math.min(completed,required)/required*100;
  return {remaining:Math.max(0,required-completed),additional:Math.max(0,required-completed-inProgress),completeWidth,progressWidth:Math.min(inProgress,Math.max(0,required-completed))/required*100,percent:Math.round(completeWidth)};
}
export function coveredByPlan(item) {
 if(item.status==='complete')return true;
 const chart=progress(item);
 return item.status==='in_progress' && chart!==null && chart.additional===0;
}
export function filterRequirements(items, filter) {
 return items.filter(r=>filter==='all'||(filter==='remaining'&&!coveredByPlan(r))||(filter==='complete'&&coveredByPlan(r)));
}
export function validateFile(file) {
 if (!file || !file.name.toLowerCase().endsWith('.pdf')) return 'Please choose a PDF report.';
 if (file.size>10*1024*1024) return 'This PDF is larger than 10 MB. Please choose a smaller report.';
 if (!file.size) return 'This file is empty.';
 return '';
}

export function hasMathMinorInterest(report) {
 const math=/\bmath(?:ematics)?\b|MATH_UMNR|수학|数学|數學|matem[aá]tica|mathématique|mathematik|математ|गणित/iu;
 const minor=/\bminor\b|부전공|마이너|辅修|輔修|副専攻|副修|nebenfach|дополнительн|माइनर/iu;
 return math.test(report?.minor||'') || (math.test(report?.intent||'') && minor.test(report?.intent||''));
}
