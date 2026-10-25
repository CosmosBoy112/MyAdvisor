package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AdvisorViewsTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private final CatalogDbService catalog=mock(CatalogDbService.class);private final AdvisorStore store=mock(AdvisorStore.class);
 private final AdvisorTools tools=new AdvisorTools(catalog,mock(MathMinorService.class),null,store,mapper);
 private final CatalogDbService.Program datsc=new CatalogDbService.Program(1,"DATSC_BS","Data Sciences, B.S.","2026-27",123.0,"https://bulletins.psu.edu/ds",Map.of("COMPUTATIONAL",2L),Map.of("COMPUTATIONAL","Computational Data Sciences"));
 private AdvisorTools.Turn turn()throws Exception{
  var report=mapper.readTree("""
   {"major":"Data Sciences (BS)","requirements":[{"title":"Prescribed Courses","status":"in_progress"},{"title":"GenEd","status":"completed"},{"title":"Supporting","status":"in_progress","coveredByPlan":true}],
    "courseInventory":{"courses":[{"code":"CMPSC 131","grade":"A","status":"recorded"},{"code":"MATH 141","grade":"IP","status":"in_progress"}]}}""");
  var t=new AdvisorTools.Turn(report,List.of(),Optional.of(new CatalogDbService.Scope("matched",datsc,"COMPUTATIONAL","major","2026-27")),false);
  t.subject="auth0|owner";t.reportId=UUID.randomUUID();return t;
 }
 private JsonNode args(String json)throws Exception{return mapper.readTree(json);}

 @Test void showViewRecordsOnlyTheRequestAndValidatesIt()throws Exception{
  var t=turn();
  assertEquals("INVALID_ARGUMENT",tools.call("show_view",args("{\"view\":\"dashboard\"}"),t).at("/error/code").asText());
  assertEquals("INVALID_ARGUMENT",tools.call("show_view",args("{\"view\":\"course_detail\"}"),t).at("/error/code").asText(),"course_detail needs courses");
  assertTrue(tools.call("show_view",args("{\"view\":\"course_detail\",\"course_codes\":[\"cmpsc465\",\"DS 200\",\"DS 220\",\"DS 300\"]}"),t).path("ok").asBoolean());
  assertEquals(List.of("CMPSC 465","DS 200","DS 220"),mapper.convertValue(t.viewRequest.path("course_codes"),List.class),"codes normalized and capped at 3");
  assertEquals("RATE_LIMITED",tools.call("show_view",args("{\"view\":\"minor_check\"}"),t).at("/error/code").asText(),"one screen per answer");
  verifyNoInteractions(catalog);
 }
 @Test void noRequestMeansNoView()throws Exception{assertNull(AdvisorViews.build(tools,turn(),mapper));}
 @Test void courseDetailIsFilledFromCatalogAndPrerequisiteTools()throws Exception{
  var t=turn();tools.call("show_view",args("{\"view\":\"course_detail\",\"course_codes\":[\"CMPSC 465\"]}"),t);
  when(catalog.courses(List.of("CMPSC 465"),"2026-27")).thenReturn((ArrayNode)mapper.readTree("[{\"code\":\"CMPSC 465\",\"title\":\"Data Structures and Algorithms\",\"credits\":\"3\",\"source_url\":\"https://bulletins.psu.edu/cmpsc\"}]"));
  when(catalog.description(any(),any())).thenReturn(Optional.empty());when(catalog.courseBasics("CMPSC 465","2026-27")).thenReturn(Optional.of(Map.of("id",4L,"prereq_text","CMPSC 360 (C or better)","source_url","https://bulletins.psu.edu/cmpsc")));
  when(catalog.prereqTree(4L)).thenReturn(mapper.readTree("{\"type\":\"ANY\",\"children\":[{\"type\":\"COURSE\",\"course\":\"CMPSC 131\"},{\"type\":\"COURSE\",\"course\":\"CMPSC 132\"}]}"));
  when(catalog.crosslists(any())).thenReturn(Map.of());
  var view=AdvisorViews.build(tools,t,mapper);
  assertEquals("course_detail",view.path("type").asText());
  assertEquals("Data Structures and Algorithms",view.at("/data/courses/0/course/title").asText());assertEquals("met",view.at("/data/courses/0/prerequisites/status").asText());
  assertTrue(view.path("warnings").toString().contains("Enrollment restrictions"));
 }
 @Test void requirementGapListsOnlyOpenRequirementsFromTheReport()throws Exception{
  var t=turn();tools.call("show_view",args("{\"view\":\"requirement_gap\"}"),t);
  var open=AdvisorViews.build(tools,t,mapper).at("/data/requirements");
  assertEquals(1,open.size(),"completed and covered-by-plan requirements are left out");assertEquals("Prescribed Courses",open.get(0).path("title").asText());
 }
 @Test void semesterPlanMarksTheReportsCoursesOnTheBulletinPlan()throws Exception{
  var t=turn();tools.call("show_view",args("{\"view\":\"semester_plan\"}"),t);
  when(catalog.policies(datsc,"COMPUTATIONAL")).thenReturn((ArrayNode)mapper.readTree("""
   [{"key":"suggested_academic_plan.COMPUTATIONAL","source_url":"https://bulletins.psu.edu/ds","description":"Bulletin","value":{"total_credits":123,"terms":[{"year":1,"season":"FA","total_credits":15,
    "courses":[{"label":"CMPSC 121 or 131 (GQ)","credits":3},{"label":"MATH 141 (GQ)","credits":4},{"label":"DS 220","credits":3},{"label":"General Education Course","credits":3}]}]}}]"""));
  var view=AdvisorViews.build(tools,t,mapper);var courses=view.at("/data/terms/0/courses");
  assertEquals("reference_plan",view.at("/data/status").asText());
  assertEquals("recorded:A",courses.get(0).path("inReport").asText(),"CMPSC 131 satisfies 'CMPSC 121 or 131'");
  assertEquals("in_progress",courses.get(1).path("inReport").asText());assertEquals("not_in_report",courses.get(2).path("inReport").asText());assertEquals("generic",courses.get(3).path("inReport").asText());
  assertTrue(view.path("warnings").toString().contains("not a personalized"));
 }
 @Test void semesterPlanWithoutAKnownOptionIsUnavailable()throws Exception{
  var t=new AdvisorTools.Turn(turn().report,List.of(),Optional.of(new CatalogDbService.Scope("matched",datsc,null,null,"2026-27")),false);
  tools.call("show_view",args("{\"view\":\"semester_plan\"}"),t);
  assertEquals("unavailable",AdvisorViews.build(tools,t,mapper).at("/data/status").asText());
 }
 @Test void programsWithoutOptionsUseTheirDefaultPlan()throws Exception{
  var ee=new CatalogDbService.Program(7,"EE_BS","Electrical Engineering, B.S.","2026-27",127.0,"https://bulletins.psu.edu/ee",Map.of(),Map.of());
  var t=new AdvisorTools.Turn(turn().report,List.of(),Optional.of(new CatalogDbService.Scope("matched",ee,null,null,"2026-27")),false);
  tools.call("show_view",args("{\"view\":\"semester_plan\"}"),t);
  when(catalog.policies(ee,null)).thenReturn((ArrayNode)mapper.readTree("""
   [{"key":"catalog_snapshot","description":"x","source_url":"https://bulletins.psu.edu/ee"},
    {"key":"suggested_academic_plan.DEFAULT","source_url":"https://bulletins.psu.edu/ee","description":"Bulletin","value":{"total_credits":127,"terms":[{"year":1,"season":"FA","total_credits":4,"courses":[{"label":"MATH 140 (GQ)","credits":4}]}]}}]"""));
  var view=AdvisorViews.build(tools,t,mapper);
  assertEquals("reference_plan",view.at("/data/status").asText());assertFalse(view.path("data").has("option"),"DEFAULT is not shown as an option");
  assertEquals("Electrical Engineering, B.S.",view.at("/data/program").asText());
 }
 @Test void planLabelsExpandBareNumbersWithTheLastSubject(){
  assertEquals(List.of("CMPSC 121","CMPSC 131"),AdvisorViews.codes("CMPSC 121 or 131 (GQ)"));
  assertEquals(List.of("CAS 100A","CAS 100B"),AdvisorViews.codes("CAS 100A or 100B (GWS)"));
  assertEquals(List.of(),AdvisorViews.codes("General Education Course"));
 }

 @Test void memoryToolsAreScopedToTheSubjectAndReported()throws Exception{
  var t=turn();
  when(store.saveMemory("auth0|owner",t.reportId,"preference","Avoid 8 am classes",null)).thenReturn(Map.of("id",3L,"category","preference","content","Avoid 8 am classes"));
  var saved=tools.call("save_memory",args("{\"category\":\"preference\",\"content\":\"Avoid 8 am classes\"}"),t);
  assertTrue(saved.path("ok").asBoolean());assertEquals("saved",t.memoryChanges.get(0).get("action"));
  when(store.forgetMemory("auth0|owner",3L)).thenReturn(true);when(store.forgetMemory("auth0|owner",99L)).thenReturn(false);
  assertTrue(tools.call("forget_memory",args("{\"id\":3}"),t).path("ok").asBoolean());
  assertEquals("NOT_FOUND",tools.call("forget_memory",args("{\"id\":99}"),t).at("/error/code").asText());
  assertEquals("INVALID_ARGUMENT",tools.call("save_memory",args("{\"category\":\"grades\",\"content\":\"x\"}"),t).at("/error/code").asText());
  assertEquals("INVALID_ARGUMENT",tools.call("save_memory",args("{\"category\":\"other\",\"content\":\""+"x".repeat(501)+"\"}"),t).at("/error/code").asText());
 }
 @Test void memoryToolsAreOnlyDeclaredWithASubject()throws Exception{
  assertTrue(tools.declarations(turn()).toString().contains("\"save_memory\""));
  var anonymous=new AdvisorTools.Turn(turn().report,List.of(),Optional.empty(),false);
  assertFalse(tools.declarations(anonymous).toString().contains("\"save_memory\""));
  assertEquals("DATA_NOT_AVAILABLE",tools.call("save_memory",args("{\"category\":\"goal\",\"content\":\"x\"}"),anonymous).at("/error/code").asText());
  verifyNoInteractions(store);
 }
 @Test void turnLoadsTheSubjectsMemories()throws Exception{
  when(catalog.scope(any())).thenReturn(Optional.empty());when(store.memories("auth0|owner")).thenReturn(List.of(Map.of("id",1L,"category","goal","content","Data engineering")));
  var t=tools.turn(turn().report,List.of(),"auth0|owner",UUID.randomUUID());
  assertEquals(1,t.memories.size());
  var agent=new AdvisorAgent(mock(GeminiService.class),tools,mapper,true);
  assertTrue(agent.input(t.report,t,"q",List.of()).contains("Data engineering"),"memories are part of the always-present context");
 }

 @Test void chatLogKeepsTheViewAndStaysReadableWithoutIt()throws Exception{
  var view=mapper.createObjectNode().put("type","course_detail");
  var row=ChatMessageCodec.decode(Map.of("role","assistant","content",ChatMessageCodec.encode("answer",null,view)));
  assertEquals("answer",row.get("content"));assertEquals(view,row.get("view"));assertFalse(row.containsKey("webResearch"));
  assertEquals("plain",ChatMessageCodec.encode("plain",null,null));assertEquals("plain",ChatMessageCodec.decode(Map.of("role","assistant","content","plain")).get("content"));
 }
 @Test void agentReturnsTheBuiltViewAndLogsTheRun()throws Exception{
  var gemini=mock(GeminiService.class);when(gemini.model()).thenReturn("gemini-test");
  var t=turn();var mockedTools=spy(tools);doReturn(t).when(mockedTools).turn(any(),any(),any(),any());
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any()))
   .thenReturn(mapper.readTree("{\"parts\":[{\"functionCall\":{\"name\":\"show_view\",\"args\":{\"view\":\"requirement_gap\"}}}]}"))
   .thenReturn(mapper.readTree("{\"parts\":[{\"text\":\"Here is what remains. %s\"}]}".formatted("Prescribed Courses still needs work. ".repeat(10))));
  var agent=new AdvisorAgent(gemini,null,mockedTools,store,mapper,true,"gemini");
  var result=agent.chat(t.report,"What is left?",List.of(),"auth0|owner",t.reportId);
  assertEquals("requirement_gap",result.view().path("type").asText());
  verify(store).saveRun(any(),any(),eq("auth0|owner"),eq(t.reportId),eq("gemini-agent:gemini-test"),eq("ok"),anyLong(),eq(2),eq(0),eq("requirement_gap"),isNull(),argThat(steps->steps.size()==3));
 }
 @Test void failedRunsAreLoggedAsErrors()throws Exception{
  var gemini=mock(GeminiService.class);when(gemini.model()).thenReturn("gemini-test");when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenThrow(new UpstreamFailure("down"));
  when(catalog.scope(any())).thenReturn(Optional.empty());
  var agent=new AdvisorAgent(gemini,null,tools,store,mapper,true,"gemini");
  assertThrows(UpstreamFailure.class,()->agent.chat(turn().report,"q",List.of(),"auth0|owner",UUID.randomUUID()));
  verify(store).saveRun(any(),any(),eq("auth0|owner"),any(),any(),eq("error"),anyLong(),eq(1),eq(0),isNull(),eq("UpstreamFailure"),any());
 }

 @Test void screenAndMemoryToolsStillRunAfterTheDataBudget()throws Exception{
  var gemini=mock(GeminiService.class);var t=turn();var spied=spy(tools);doReturn(t).when(spied).turn(any(),any(),any(),any());
  var many=new StringBuilder("{\"parts\":[");for(int i=0;i<AdvisorAgent.MAX_TOOL_CALLS;i++)many.append("{\"functionCall\":{\"name\":\"get_student_record\",\"args\":{\"section\":\"summary\"}}},");
  many.append("{\"functionCall\":{\"name\":\"get_student_record\",\"args\":{\"section\":\"summary\"}}},{\"functionCall\":{\"name\":\"show_view\",\"args\":{\"view\":\"requirement_gap\"}}}]}");
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(mapper.readTree(many.toString())).thenReturn(mapper.readTree("{\"parts\":[{\"text\":\"done\"}]}"));
  var result=new AdvisorAgent(gemini,null,spied,null,mapper,true,"gemini").chat(t.report,"q",List.of(),"auth0|owner",t.reportId);
  assertEquals("requirement_gap",result.view().path("type").asText(),"show_view is not refused by the data-tool budget");
  var oks=new ArrayList<Boolean>();result.toolCalls().forEach(c->oks.add(c.path("ok").asBoolean()));
  assertFalse(oks.get(AdvisorAgent.MAX_TOOL_CALLS),"the ninth data call is refused");assertTrue(oks.getLast());
 }
 @Test void memoriesSavedByAFailedLuminalRunAreStillReported()throws Exception{
  var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);when(luminal.configured()).thenReturn(true);when(luminal.model()).thenReturn("m");when(gemini.model()).thenReturn("g");
  when(catalog.scope(any())).thenReturn(Optional.empty());when(store.memories(any())).thenReturn(List.of());
  when(store.saveMemory(any(),any(),any(),any(),any())).thenReturn(Map.of("id",7L,"category","preference","content","No summer terms"));
  when(luminal.agentTurn(any(),any(),anyBoolean(),any()))
   .thenReturn(mapper.readTree("{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\",\"function\":{\"name\":\"save_memory\",\"arguments\":\"{\\\"category\\\":\\\"preference\\\",\\\"content\\\":\\\"No summer terms\\\"}\"}}]}"))
   .thenThrow(new AnalysisTimeout("slow"));
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(mapper.readTree("{\"parts\":[{\"text\":\"Noted.\"}]}"));
  var result=new AdvisorAgent(gemini,luminal,tools,store,mapper,true,"luminal").chat(turn().report,"No summer terms for me",List.of(),"auth0|owner",UUID.randomUUID());
  assertEquals("gemini-agent",result.provider());assertEquals(1,result.memoryChanges().size());assertEquals(7L,result.memoryChanges().get(0).get("id"));
 }
 @Test void requirementOverviewReplacesCourseListsWithCounts()throws Exception{
  var t=turn();var tree=(ArrayNode)mapper.readTree("[{\"id\":1,\"label\":\"Prescribed\",\"bulletin_text\":\"long\",\"courses\":[{\"code\":\"DS 200\"},{\"code\":\"DS 220\"}],\"children\":[{\"id\":2,\"courses\":[{\"code\":\"X 1\"}]}]}]");
  when(catalog.requirements(any(),any(),eq(true),isNull())).thenReturn(tree);
  var overview=tools.call("get_program_requirements",args("{}"),t).at("/data/requirements/0");
  assertEquals(2,overview.path("course_count").asInt());assertFalse(overview.has("courses"));assertFalse(overview.has("bulletin_text"));assertEquals(1,overview.at("/children/0/course_count").asInt());
  var sub=(ArrayNode)mapper.readTree("[{\"id\":2,\"courses\":[{\"code\":\"X 1\"}]}]");when(catalog.requirements(any(),any(),eq(true),eq(2L))).thenReturn(sub);
  assertTrue(tools.call("get_program_requirements",args("{\"node_id\":2}"),t).at("/data/requirements/0").has("courses"),"a subtree keeps its courses");
 }

 @Test void shortPennStateCourseCodesAreAccepted()throws Exception{
  for(String code:List.of("ASTRO 7N","ENGL 15","CAS 100A","cmpsc465","MATH 140H"))assertNotNull(AdvisorTools.code(mapper.createObjectNode().put("code",code)),code);
  assertEquals("ASTRO 7N",AdvisorTools.code(mapper.createObjectNode().put("code","astro 7n")));
  assertThrows(IllegalArgumentException.class,()->AdvisorTools.code(mapper.createObjectNode().put("code","algorithms")));
  assertEquals(List.of("ENGL 15"),AdvisorViews.codes("ENGL 15 (GWS)"));
 }
 @Test void anEmptyFinalMessageIsAskedForOnceMore()throws Exception{
  var luminal=mock(LuminalService.class);when(luminal.configured()).thenReturn(true);when(luminal.model()).thenReturn("m");when(catalog.scope(any())).thenReturn(Optional.empty());
  var sent=new ArrayList<String>();
  when(luminal.agentTurn(any(),any(),anyBoolean(),any())).thenAnswer(i->{sent.add(i.getArgument(0).toString());return sent.size()==1?mapper.readTree("{\"role\":\"assistant\",\"content\":\"\"}"):mapper.readTree("{\"role\":\"assistant\",\"content\":\"Here you go.\"}");});
  var result=new AdvisorAgent(mock(GeminiService.class),luminal,tools,null,mapper,true,"luminal").chat(turn().report,"q",List.of());
  assertEquals("luminal-agent",result.provider());assertEquals("Here you go.",result.text());
  assertTrue(sent.get(1).contains(AdvisorAgent.ANSWER_NOW),"the retry asks explicitly for an answer");
 }

 @Test void profileGivesVagueQuestionsTheirContext()throws Exception{
  var report=mapper.readTree("""
   {"major":"Data Sciences (BS)","campus":"University Park","catalogYear":"2026-27","intent":"Math minor",
    "requirements":[{"title":"Prescribed","status":"in_progress","required":40,"completed":28,"inProgress":6},{"title":"GenEd","status":"completed"},{"title":"Supporting","status":"in_progress","coveredByPlan":true}],
    "courseInventory":{"courses":[{"code":"MATH 140","grade":"W"},{"code":"MATH 140","grade":"B+"},{"code":"MATH 230","grade":"IP"},{"code":"STAT 200","grade":"F"},{"code":"DS 200","grade":"A","requiresReview":true}]}}""");
  var history=List.<Map<String,Object>>of(Map.of("role","user","content","Can I take CMPSC 465?"),Map.of("role","assistant","content","Not yet; you need CMPSC 132."));
  var p=AdvisorTools.profile(report,Optional.of(new CatalogDbService.Scope("matched",datsc,"COMPUTATIONAL","major","2026-27")),history,"is it hard?",mapper);
  assertEquals("Penn State University",p.path("school").asText());assertEquals("Computational Data Sciences",p.path("option").asText());
  assertEquals("[\"MATH 140 (B+)\"]",p.at("/courses/completed").toString(),"a retake counts over the withdrawal");
  assertEquals("[\"MATH 230\"]",p.at("/courses/inProgress").toString());assertEquals("[\"STAT 200 (F)\"]",p.at("/courses/notEarned").toString());assertEquals("[\"DS 200 (A)\"]",p.at("/courses/needsReview").toString());
  assertEquals(1,p.path("openRequirements").size());assertEquals("[\"Supporting\"]",p.path("coveredByCurrentCourses").toString());assertEquals(1,p.path("completedRequirementCount").asInt());
  assertEquals("CMPSC 132",p.at("/recentlyDiscussedCourses/0").asText(),"the latest message's course comes first");assertTrue(p.path("recentlyDiscussedCourses").toString().contains("CMPSC 465"));
 }
 @Test void searchContextIsPublicCampusAndProgram()throws Exception{
  var report=mapper.readTree("{\"major\":\"Data Sciences (BS)\",\"campus\":\"University Park\",\"courseInventory\":{\"courses\":[{\"code\":\"MATH 140\",\"grade\":\"B\"}]}}");
  String ctx=AdvisorTools.searchContext(report,Optional.of(new CatalogDbService.Scope("matched",datsc,null,null,null)));
  assertEquals("University Park|Data Sciences",ctx);assertFalse(ctx.contains("MATH"));
  assertEquals("best electives Penn State University Park Data Sciences",WebResearchService.tavilyQuery("best electives",List.of(),ctx));
  assertEquals("CMPSC 465 workload Penn State University Park",WebResearchService.tavilyQuery("CMPSC 465 workload",List.of(),ctx),"a course code is specific enough without the program");
  assertEquals("Penn State University Park Data Sciences internships",WebResearchService.tavilyQuery("Penn State University Park Data Sciences internships",List.of(),ctx),"nothing is added twice");
  assertEquals("Computer Science",AdvisorTools.searchContext(mapper.readTree("{\"major\":\"Computer Science, B.S. (Engineering)\"}"),Optional.empty()).split("\\|")[1]);
 }

 @Test void coursesShownOnlyOnAnEarlierScreenCountAsRecentlyDiscussed()throws Exception{
  var view=mapper.readTree("{\"type\":\"course_options\",\"data\":{\"courses\":[{\"code\":\"STAT 414\"},{\"code\":\"DS 435\"}]}}");
  var history=List.<Map<String,Object>>of(Map.of("role","user","content","what next?"),Map.of("role","assistant","content","Here are good options.","view",view));
  var p=AdvisorTools.profile(mapper.readTree("{}"),Optional.empty(),history,"is it hard?",mapper);
  assertEquals("[\"STAT 414\",\"DS 435\"]",p.path("recentlyDiscussedCourses").toString());
 }

 @Test void toolMarkupWrittenAsTextIsNeverShown()throws Exception{
  assertTrue(AdvisorAgent.leakedToolCall("<｜｜DSML｜｜ calls>\n<｜｜DSML｜｜ invoke name=\"show_view\">"));
  assertTrue(AdvisorAgent.leakedToolCall("<function_calls><invoke name=\"x\">"));
  assertFalse(AdvisorAgent.leakedToolCall("CMPSC 465 needs CMPSC 360 < 3 times"));
  var luminal=mock(LuminalService.class);when(luminal.configured()).thenReturn(true);when(luminal.model()).thenReturn("m");when(catalog.scope(any())).thenReturn(Optional.empty());
  when(luminal.agentTurn(any(),any(),anyBoolean(),any())).thenReturn(mapper.readTree("{\"role\":\"assistant\",\"content\":\"<｜｜DSML｜｜ calls>\"}")).thenReturn(mapper.readTree("{\"role\":\"assistant\",\"content\":\"Clean answer.\"}"));
  assertEquals("Clean answer.",new AdvisorAgent(mock(GeminiService.class),luminal,tools,null,mapper,true,"luminal").chat(turn().report,"q",List.of()).text());
 }

 @Test void anAnswerThatOnlyPointsAtTheScreenIsAskedForAgain()throws Exception{
  var gemini=mock(GeminiService.class);var t=turn();var spied=spy(tools);doReturn(t).when(spied).turn(any(),any(),any(),any());
  var sent=new ArrayList<String>();
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenAnswer(i->{sent.add(i.getArgument(1).toString());return switch(sent.size()){
   case 1->mapper.readTree("{\"parts\":[{\"functionCall\":{\"name\":\"show_view\",\"args\":{\"view\":\"requirement_gap\"}}}]}");
   case 2->mapper.readTree("{\"parts\":[{\"text\":\"I've opened the screen.\"}]}");
   default->mapper.readTree("{\"parts\":[{\"text\":\"Prescribed Courses is the open requirement; take DS 435 next because its prerequisite DS 220 is done.\"}]}");};});
  var result=new AdvisorAgent(gemini,null,spied,null,mapper,true,"gemini").chat(t.report,"what is left?",List.of(),"auth0|owner",t.reportId);
  assertTrue(result.text().startsWith("Prescribed Courses is the open requirement"));assertTrue(sent.get(2).contains("only pointed at the screen"));
  assertEquals("requirement_gap",result.view().path("type").asText());
 }
}
