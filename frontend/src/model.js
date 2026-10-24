export function progress(totals) {
  const {required,completed,inProgress} = totals || {};
  if (![required,completed,inProgress].every(Number.isFinite) || required <= 0 || completed < 0 || inProgress < 0) return null;
  const completeWidth=Math.min(completed,required)/required*100;
  return {remaining:Math.max(0,required-completed-inProgress),completeWidth,progressWidth:Math.min(inProgress,Math.max(0,required-completed))/required*100,percent:Math.round(completeWidth)};
}
export function filterRequirements(items, filter) {
 return items.filter(r=>filter==='all'||(filter==='remaining'&&r.status!=='complete')||(filter==='complete'&&r.status==='complete'));
}
export function validateFile(file) {
 if (!file || !file.name.toLowerCase().endsWith('.pdf')) return 'Please choose a PDF report.';
 if (file.size>10*1024*1024) return 'This PDF is larger than 10 MB. Please choose a smaller report.';
 if (!file.size) return 'This file is empty.';
 return '';
}
