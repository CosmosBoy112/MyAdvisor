import {ref} from 'vue';
// UI translations are intentionally explicit; report evidence and course names stay original.
export const locale=ref('en');
export function detectInputLanguage(text,current='en'){
 if(/[가-힣ㄱ-ㅎㅏ-ㅣ]/u.test(text||''))return 'ko';
 if(/[a-z]{2,}/i.test(text||''))return 'en';
 return current;
}
export function useInputLanguage(text){locale.value=detectInputLanguage(text,locale.value);}
export const ko={
'Courses for this requirement':'요건 충족에 사용할 과목',
'This requirement is already marked complete.':'이미 완료로 표시된 요건입니다.',
'Options listed in your PDF':'PDF에 명시된 선택 과목',
'Follow the report’s selection rules; these are not confirmed remaining courses.':'보고서의 선택 조건을 따르세요. 아래 과목이 모두 미이수 과목이라는 뜻은 아닙니다.',
'No course options listed in the PDF. Check the catalog below or ask about this requirement.':'PDF에 선택 과목이 없습니다. 카탈로그가 있으면 아래에서 확인하거나 이 요건에 대해 질문하세요.',
'Help me choose courses':'어떤 과목을 들을지 질문하기',
'How to satisfy this requirement':'요건 충족 방법',
'Bulletin course options':'Bulletin 참고 과목',
'Complete all groups below':'아래 모든 그룹 충족',
'Choose groups:':'선택할 그룹 수:',
'Choose courses:':'선택할 과목 수:',
'credits minimum':'학점 이상',
'Minimum grade:':'최소 성적:',
'Course attribute:':'과목 속성:',
'Selection requires confirmation':'선택 조건 확인 필요',
'Approved course list unavailable — confirm with your adviser.':'승인 과목 목록 미확인 — 지도교수와 확인하세요.',

'Upload your LionPATH What-If Report.':'LionPATH What-If 보고서를 업로드하세요.',
'Sample data · no live AI analysis.':'샘플 데이터 · 실제 AI 분석 아님',
'Gray assumes current courses are passed.':'회색 구간은 수강 과목 통과 후 기준입니다.',
'In-progress credits are not earned yet. Requirements may overlap; course eligibility needs confirmation.':'수강 중 학점은 아직 이수 전입니다. 요건 간 학점은 중복될 수 있으며, 과목 인정 여부는 확인이 필요합니다.',

'Built for Penn State students':'펜스테이트 학생을 위한 서비스','Checking session…':'로그인 확인 중…','My reports':'내 보고서','Sign in':'로그인','Sign out':'로그아웃',
'LESS GUESSWORK. MORE POSSIBILITY.':'명확한 학업 계획, 더 넓은 가능성','Your next chapter,':'다음 학기를,','clearer.':'더 명확하게.','Your degree report has the details.':'졸업 요건 보고서의 복잡한 정보를','Let’s turn them into a direction.':'실행할 수 있는 계획으로 바꿔보세요.',
'Your major and the minor you are considering':'전공과 고려 중인 부전공','Attach report':'보고서 첨부','Allow report text to be sent to Gemini (and Luminal for planning questions) for analysis. Remove personal identifiers before uploading.':'분석을 위해 보고서 내용을 Gemini 및 계획 질문 처리용 Luminal에 전송하는 데 동의합니다. 업로드 전 개인 식별 정보를 제거하세요.',
'Retry':'다시 시도','AI analysis can take up to 100 seconds. You can stop waiting at any time.':'AI 분석은 최대 100초 걸릴 수 있습니다. 언제든 대기를 중단할 수 있습니다.','Stop waiting':'대기 중단','Start with your LionPATH What-If Report':'LionPATH What-If 보고서로 시작하세요','Explore a sample':'샘플 보기','Understand your requirements':'졸업 요건 이해하기','See what’s left':'남은 요건 확인하기','Ask better questions':'보고서에 질문하기','A little clarity for a big decision.':'중요한 결정을 더 명확하게.',
'Home':'홈','Your degree overview':'학위 진행 현황','SAMPLE REPORT':'샘플 보고서','YOUR BIG PICTURE':'전체 학업 현황','A clearer path forward':'더 명확한 졸업 계획','Delete report':'보고서 삭제','New report':'새 보고서','You’re exploring an illustrative report. No personal data or live AI is used.':'예시 보고서입니다. 개인정보나 실제 AI 분석을 사용하지 않습니다.',
'Degree progress':'학위 진행률','credits completed':'학점 이수 완료','Some credit totals need confirmation. We haven’t filled in missing values.':'일부 학점은 확인이 필요합니다. 누락된 값은 임의로 채우지 않습니다.','Completed':'이수 완료','After current courses':'수강 과목 이후 추가 필요','credits still to earn':'학점 추가 이수 필요',', including':'(포함:','in progress. The gray segment assumes current courses are passed.':'학점 수강 중). 회색 구간은 수강 중인 과목을 통과한 이후 기준입니다.','Requirement totals may overlap.':'요건 간 학점은 중복될 수 있습니다.',
'Your requirements':'졸업 요건','of':'/','listed requirements complete':'개 요건 완료','in progress':'학점 수강 중','More information needed':'추가 확인 필요','No eligible course list was extracted for this item.':'이 항목의 인정 과목 목록을 추출하지 못했습니다.','· p.':'· 페이지','Ask about this':'이 요건 질문하기','No requirements in this view.':'표시할 요건이 없습니다.','Export analysis':'분석 내보내기','Ask your report':'보고서에 질문하기','A student-built project':'학생들이 만든 프로젝트','Built by BLS':'제작 BLS','Independent of Penn State. Confirm academic decisions with your adviser.':'펜스테이트 공식 서비스가 아닙니다. 학업 결정은 지도교수와 확인하세요.',
'Report evidence':'보고서 근거','· Page':'· 페이지','Your saved reports':'저장한 보고서','Reopen a report and pick up your conversation.':'보고서를 다시 열고 대화를 이어가세요.','Loading reports…':'보고서 불러오는 중…','No saved reports yet. Upload a report to get started.':'저장된 보고서가 없습니다. PDF를 업로드해 시작하세요.','Delete':'삭제','Delete this report?':'이 보고서를 삭제할까요?','The saved analysis and its conversation will be permanently removed. You can upload the PDF again later.':'저장된 분석과 대화가 영구 삭제됩니다. PDF는 나중에 다시 업로드할 수 있습니다.','Keep report':'보고서 유지','Hi':'안녕하세요', '. Let’s make sense of your report. What would you like to understand?':'님. 보고서에서 어떤 내용이 궁금하신가요?','Reading your report…':'보고서 확인 중…','Question about your report':'보고서에 대한 질문','Enter to send · Shift+Enter for a new line':'Enter 전송 · Shift+Enter 줄바꿈','Confirm academic decisions with your adviser.':'학업 결정은 지도교수와 확인하세요.',
'Credit breakdown':'학점 구성','AT A GLANCE':'한눈에 보기','credits ·':'학점 ·','As of the report date.':'보고서 작성일 기준입니다.',', including current courses.':'(수강 중 과목 포함).','additional credits needed if current courses are passed.':'학점은 수강 중인 과목을 통과한 뒤에도 추가로 필요합니다.','Upload a report with complete credit totals to see the breakdown.':'학점 합계가 표시된 보고서를 업로드하세요.','Select a row to see its details.':'항목을 선택하면 상세 내용을 볼 수 있습니다.','To complete (':'미완료 (','Completed (':'완료 (','In progress':'수강 중','Needs review':'검토 필요','Pending current courses':'수강 중 과목 완료 대기','Complete':'완료','Needs confirmation':'확인 필요','needed after current courses':'학점은 수강 과목 이후 추가 필요','Showing':'표시 중:','requirements':'개 요건',
'Each bar shows progress toward its own requirement. Still to earn = required minus completed. In-progress credits are not earned yet. The gray segment shows additional credits needed after current courses are passed. Requirements can overlap; do not add their credits together. Credit progress does not verify course eligibility.':'각 막대는 해당 요건의 진행률입니다. 남은 학점은 요구 학점에서 이수 완료 학점을 뺀 값입니다. 수강 중인 학점은 아직 이수 완료가 아닙니다. 회색 구간은 현재 과목 통과 후 추가로 필요한 학점입니다. 요건 간 학점은 중복될 수 있으므로 합산하지 마세요. 진행률은 과목 인정 여부를 보장하지 않습니다.',
'Official catalog comparison':'공식 학사 요건 비교','report sections matched':'개 보고서 항목 연결','· Bulletin minimum:':'· Bulletin 최소:','credits. Matches need review.':'학점. 연결 결과는 검토가 필요합니다.','The PDF total differs from the Bulletin minimum. Your PDF totals have been preserved.':'PDF 합계가 Bulletin 최소 학점과 다릅니다. PDF의 원래 값은 유지했습니다.','Official Bulletin ↗':'공식 Bulletin ↗','Handbook · historical reference ↗':'Handbook · 이전 학년도 참고 ↗','Bulletin comparison · needs review':'Bulletin 비교 · 검토 필요','Current Bulletin requirement:':'현재 Bulletin 요구 학점:','credits':'학점','This differs from the PDF requirement. Confirm the applicable rules with your adviser.':'PDF 요건과 다릅니다. 적용 규정은 지도교수와 확인하세요.','course codes listed in the current Bulletin':'개 과목이 현재 Bulletin에 등재되어 있습니다.','These are reference options, not a personalized eligibility check. Selection rules and prerequisites still apply.':'참고용 과목 목록입니다. 개인별 수강 자격 판정이 아니며 선택 조건과 선수과목 요건이 적용됩니다.','Prerequisites:':'선수과목:','Prerequisite interpretation needs review.':'선수과목 해석에 검토가 필요합니다.','View official requirement ↗':'공식 요건 보기 ↗',
'What’s left, by requirement':'요건별 남은 학점','Completed requirements':'완료한 요건','Requirements marked complete in your report.':'보고서에서 완료로 표시된 요건입니다.','Largest credit gaps first.':'부족한 학점이 많은 순서입니다.','completed':'완료','unfinished or unverified':'미완료 또는 미확인','No requirements were identified in this report.':'보고서에서 요건을 찾지 못했습니다.','No requirements are marked complete in this report yet. In-progress and needs-review items are under “To complete”.':'완료로 표시된 요건이 없습니다. 수강 중·검토 필요 항목은 미완료 탭에서 확인하세요.','All listed requirements are marked complete. Select “Completed” to view them.':'모든 요건이 완료로 표시되었습니다. 완료 탭에서 확인하세요.','View details →':'상세 보기 →','Credit totals need confirmation →':'학점 확인 필요 →','Unknown totals':'학점 미확인','In progress / planned':'수강 중 / 예정','Remaining':'남은 요건','All':'전체','To complete':'미완료',
'Major needs confirmation':'전공 확인 필요','Campus not specified':'캠퍼스 미표기','Requirement year unknown':'적용 학년도 미확인','Sample conversation':'샘플 대화','Answers grounded in your degree report':'학위 보고서를 근거로 답변합니다','View source':'근거 보기','Review unverified source':'미확인 근거 검토','Illustrative source':'예시 근거','Extracted source':'추출한 근거','Deleting…':'삭제 중…','Academic report':'학업 보고서',
'Verifying your sign-in…':'로그인 확인 중…','Uploading and analyzing your report…':'보고서 업로드 및 분석 중…','Ask about a requirement…':'궁금한 요건을 질문하세요…','How many credits are left?':'남은 학점이 몇 학점인가요?','Explain Department List':'Department List를 설명해 주세요','Is my minor included?':'부전공이 포함되어 있나요?',
'I’m majoring in Computer Science, considering a Math minor…':'컴퓨터과학을 전공하고 수학 부전공을 고려하고 있어요…','Report and conversation deleted.':'보고서와 대화를 삭제했습니다.','Title not verified':'과목명 미확인',
'Switch to light mode':'라이트 모드로 전환','Switch to dark mode':'다크 모드로 전환','Close chat':'채팅 닫기','Send question':'질문 전송','Analyze report':'보고서 분석','Remove PDF':'PDF 제거','Dismiss':'닫기','Dismiss error':'오류 닫기','Close evidence':'근거 닫기','Saved reports':'저장한 보고서','Close saved reports':'저장 보고서 닫기','Enter to analyze · Shift+Enter for a new line':'Enter 분석 · Shift+Enter 줄바꿈','Enter to send · Shift+Enter for a new line':'Enter 전송 · Shift+Enter 줄바꿈',
};
Object.assign(ko,{
  "Credit totals and requirement completion are separate. Review AI extraction against the original report and confirm decisions with an adviser.": "학점 합계와 요건 충족 여부는 별개입니다. AI 추출 결과를 원본과 대조하고 학업 결정은 지도교수와 확인하세요.",
  "Degree totals were reconciled against the report's degree-total course table. 'Used' includes IP credits; IP can include future registered terms. These figures describe the report date, not today's enrollment.": "학위 총계 과목 표와 학점 합계를 대조했습니다. used에는 IP 학점이 포함되며, IP에는 향후 등록 학기도 포함될 수 있습니다. 보고서 작성일 기준이며 현재 등록 상태가 아닙니다.",
  "The degree summary uses 'used' credits, which may include IP. Its course table could not be fully reconciled; earned and in-progress totals remain unknown.": "used 학점에 IP가 포함될 수 있습니다. 과목 표 전체를 대조하지 못해 완료·수강 중 학점은 미확인으로 남겼습니다.",
  "This catalog covers only Computer Science B.S., University Park, 2026–27. The report's major, campus or requirement year is missing or different. No catalog rules were applied.": "이 카탈로그는 University Park 컴퓨터과학 B.S. 2026–27 기준입니다. 보고서의 전공·캠퍼스·적용 학년도가 다르거나 누락되어 규칙을 적용하지 않았습니다.",
  "Report scope could not be corroborated in the extracted PDF text. Upload a report showing the major, University Park campus and 2026–27 requirement year to compare official requirements.": "PDF 원문에서 적용 범위를 확인하지 못했습니다. 공식 요건 비교를 위해 전공, University Park 캠퍼스, 2026–27 적용 학년도가 표시된 보고서가 필요합니다.",
  "Compared with the 2026–27 Bulletin. The linked 2024–25 Handbook is historical only. Matching labels does not verify course eligibility or graduation; PDF credits and completion remain unchanged.": "2026–27 Bulletin과 비교했습니다. 연결된 2024–25 Handbook은 이전 학년도 참고 자료입니다. 명칭 연결은 수강 자격이나 졸업 요건 충족을 보장하지 않으며 PDF 학점·완료 상태는 유지합니다.",
  "No exact catalog label match. Confirm this report section with an adviser.": "정확히 일치하는 카탈로그 명칭이 없습니다. 해당 항목은 지도교수와 확인하세요.",
  "Illustrative sample": "예시 데이터",
  "Illustrative sample evidence; no personal PDF was analyzed.": "예시 근거이며 개인 PDF를 분석한 결과가 아닙니다.",
  "This text matches the extracted report page. This does not independently verify the academic interpretation.": "추출한 보고서 페이지와 일치하는 문구입니다. 학사 해석의 정확성을 별도로 보장하지는 않습니다.",
  "This citation could not be matched to the extracted page. Please check the original PDF.": "이 인용문이 원문 페이지와 일치하는지 확인하지 못했습니다. PDF를 직접 확인하세요.",
  "Same category name, different required credits; current course eligibility remains unresolved.": "항목 명칭은 같지만 요구 학점이 다릅니다. 현재 인정 과목 목록은 확인이 필요합니다.",
  "Older thematic advanced electives are not the current natural-science choice or Department List.": "이전 학년도의 주제별 고급 선택 과목은 현재 자연과학 선택이나 Department List와 같지 않습니다.",
  "Use the current enumerated 18-credit pool; the older writing/elective split is not interchangeable.": "현재 명시된 18학점 선택 목록을 사용하세요. 이전 글쓰기·선택 과목 구조와 자동으로 대체할 수 없습니다.",
  "The current prescribed list includes CMPSC 483W. Older writing-course choices do not imply an automatic substitution.": "현재 필수 목록에는 CMPSC 483W가 포함됩니다. 이전 글쓰기 선택 과목이 자동으로 대체 인정되는 것은 아닙니다.",
  "PHYS 213 or PHYS 214 are explicit choices; the other approved-list branch needs confirmation.": "PHYS 213 또는 PHYS 214가 명시된 선택지입니다. 그 밖의 승인 목록은 확인이 필요합니다.",
  "Do not add the old separate four-credit language requirement to the current program.": "이전 학년도의 별도 외국어 4학점 요건을 현재 전공 요건에 추가하지 마세요."
});
export function t(text){return locale.value==='ko'?(ko[text]??text):text;}
