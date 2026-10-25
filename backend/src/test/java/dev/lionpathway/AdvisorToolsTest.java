package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AdvisorToolsTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private final CatalogDbService catalog=mock(CatalogDbService.class);private final WebResearchService web=mock(WebResearchService.class);
 private final AdvisorTools tools=new AdvisorTools(catalog,mock(MathMinorService.class),web,mapper);
 private final CatalogDbService.Program datsc=new CatalogDbService.Program(1,"DATSC_BS","Data Sciences, B.S.","2026-27",123.0,"https://bulletins.psu.edu/ds",Map.of("COMPUTATIONAL",2L),Map.of("COMPUTATIONAL","Computational Data Sciences"));
 private JsonNode report()throws Exception{return mapper.readTree("""
  {"major":"Data Sciences (BS)","catalogYear":"2026-27","totals":{"required":123,"completed":60,"inProgress":15},"webSearchEnabled":true,
   "requirements":[{"title":"Prescribed Courses","status":"in_progress"},{"title":"GenEd Quantification","status":"completed"}],
   "courseInventory":{"method":"pdf_course_rows_v1","courseCount":2,"courses":[{"code":"CMPSC 360","term":"FA 2025","grade":"B","credits":3,"status":"recorded","requiresReview":false},{"code":"MATH 230","term":"SP 2026","grade":"IP","credits":4,"status":"in_progress","requiresReview":false}]}}""");}
 private AdvisorTools.Turn turn()throws Exception{return new AdvisorTools.Turn(report(),List.of(),Optional.of(new CatalogDbService.Scope("matched",datsc,"COMPUTATIONAL","major","2026-27")),true);}
 private JsonNode args(String json)throws Exception{return mapper.readTree(json);}

 @Test void studentRecordSectionsReadOnlyTheReport()throws Exception{
  var t=turn();
  var summary=tools.call("get_student_record",args("{\"section\":\"summary\"}"),t);
  assertTrue(summary.path("ok").asBoolean());assertEquals(60,summary.at("/data/totals/completed").asInt());assertEquals(1,summary.at("/data/requirementStatusCounts/completed").asInt());
  var ip=tools.call("get_student_record",args("{\"section\":\"courses\",\"status\":\"in_progress\"}"),t);
  assertEquals(1,ip.at("/data/courses").size());assertEquals("MATH 230",ip.at("/data/courses/0/code").asText());
  var reqs=tools.call("get_student_record",args("{\"section\":\"requirements\",\"requirement\":\"gened\"}"),t);
  assertEquals(1,reqs.at("/data/requirements").size());
  verifyNoInteractions(catalog);
 }
 @Test void checkPrerequisitesUsesReportRowsAndCatalogTree()throws Exception{
  when(catalog.courseBasics("CMPSC 465","2026-27")).thenReturn(Optional.of(Map.of("id",9L,"prereq_text","CMPSC 360 (C or better)","source_url","https://bulletins.psu.edu/cmpsc")));
  when(catalog.prereqTree(9L)).thenReturn(mapper.readTree("{\"type\":\"ALL\",\"children\":[{\"type\":\"COURSE\",\"course\":\"CMPSC 360\",\"min_grade\":\"C\"},{\"type\":\"COURSE\",\"course\":\"MATH 230\"}]}"));
  when(catalog.crosslists(any())).thenReturn(Map.of());
  when(catalog.courses(List.of("CMPSC 465"),"2026-27")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)mapper.readTree("[{\"code\":\"CMPSC 465\",\"prerequisite_text\":\"CMPSC 360 (C or better)\",\"source_url\":\"https://bulletins.psu.edu/cmpsc\"}]"));
  var result=tools.call("check_prerequisites",args("{\"code\":\"cmpsc465\"}"),turn());
  assertTrue(result.path("ok").asBoolean());assertEquals("in_progress",result.at("/data/status").asText(),"MATH 230 is still in progress");
  assertEquals("CMPSC 360 (C or better)",result.at("/data/prerequisite_text").asText());
  assertTrue(result.path("warnings").toString().contains("Enrollment restrictions"));
 }
 @Test void invalidArgumentsAndUnknownCoursesAreToolErrors()throws Exception{
  assertEquals("INVALID_ARGUMENT",tools.call("get_course",args("{\"code\":\"algorithms\"}"),turn()).at("/error/code").asText());
  assertEquals("INVALID_ARGUMENT",tools.call("search_courses",args("{}"),turn()).at("/error/code").asText());
  assertEquals("INVALID_ARGUMENT",tools.call("drop_table",args("{}"),turn()).at("/error/code").asText());
  when(catalog.courseBasics(any(),any())).thenReturn(Optional.empty());
  assertEquals("NOT_FOUND",tools.call("check_prerequisites",args("{\"code\":\"STAT 999\"}"),turn()).at("/error/code").asText());
 }
 @Test void databaseFailuresBecomeDataNotAvailable()throws Exception{
  when(catalog.searchCourses(any(),any(),any(),any(),any(),any(),anyInt())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
  var result=tools.call("search_courses",args("{\"query\":\"algorithms\"}"),turn());
  assertFalse(result.path("ok").asBoolean());assertEquals("DATA_NOT_AVAILABLE",result.at("/error/code").asText());
 }
 @Test void webSearchRunsOnceAndHidesTheSearchWidgetFromTheModel()throws Exception{
  var t=turn();when(web.search(eq("CMPSC 465 professor reviews"),any(),eq("community"),any())).thenReturn((com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("{\"status\":\"searched\",\"excerpts\":[{\"text\":\"x\",\"sourceIds\":[\"W1\"]}],\"searchEntryPoint\":\"<div/>\"}"));
  var first=tools.call("web_search",args("{\"query\":\"CMPSC 465 professor reviews\",\"sources\":\"community\"}"),t);
  assertTrue(first.path("ok").asBoolean());assertFalse(first.path("data").has("searchEntryPoint"));assertTrue(t.webResearch.has("searchEntryPoint"),"the frontend still renders it");
  assertEquals("RATE_LIMITED",tools.call("web_search",args("{\"query\":\"again\"}"),t).at("/error/code").asText());
  verify(web,times(1)).search(any(),any(),any(),any());
 }
 @Test void webSearchIsNotDeclaredWhenTurnedOff()throws Exception{
  var off=new AdvisorTools.Turn(report(),List.of(),Optional.empty(),false);
  assertFalse(tools.declarations(off).toString().contains("\"web_search\""));assertTrue(tools.declarations(turn()).toString().contains("\"web_search\""));
  assertEquals("DATA_NOT_AVAILABLE",tools.call("web_search",args("{\"query\":\"x\"}"),off).at("/error/code").asText());
 }
 @Test void requirementsDefaultToTheReportProgramAndOption()throws Exception{
  when(catalog.requirements(eq(datsc),eq(2L),eq(true),isNull())).thenReturn(mapper.createArrayNode().add(mapper.createObjectNode().put("id",5).put("label","Prescribed Courses")));
  var result=tools.call("get_program_requirements",args("{}"),turn());
  assertEquals("Computational Data Sciences",result.at("/data/option").asText());assertEquals(5,result.at("/data/requirements/0/id").asInt());
  assertEquals("INVALID_ARGUMENT",tools.call("get_program_requirements",args("{\"option\":\"APPLIED\"}"),turn()).at("/error/code").asText());
 }
 @Test void requirementCoursesAreMarkedAgainstTheReport()throws Exception{
  when(catalog.requirementCourses(7L,"2026-27",20)).thenReturn(Optional.of((com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("{\"node_id\":7,\"courses\":[{\"code\":\"CMPSC 360\"},{\"code\":\"MATH 230\"},{\"code\":\"CMPSC 465\"}]}")));
  var courses=tools.call("find_courses_for_requirement",args("{\"node_id\":7}"),turn()).at("/data/courses");
  assertEquals("recorded:B",courses.get(0).path("in_report").asText());assertEquals("in_progress",courses.get(1).path("in_report").asText());assertEquals("no",courses.get(2).path("in_report").asText());
 }

 @Test void prerequisiteChecksCanBeBatched()throws Exception{
  when(catalog.courseBasics("CMPSC 465","2026-27")).thenReturn(Optional.of(Map.of("id",9L,"prereq_text","CMPSC 360 (C or better)","source_url","https://bulletins.psu.edu/cmpsc")));when(catalog.courseBasics("STAT 999","2026-27")).thenReturn(Optional.empty());
  when(catalog.prereqTree(9L)).thenReturn(mapper.readTree("{\"type\":\"COURSE\",\"course\":\"CMPSC 360\"}"));when(catalog.crosslists(any())).thenReturn(Map.of());
  when(catalog.courses(List.of("CMPSC 465"),"2026-27")).thenReturn((com.fasterxml.jackson.databind.node.ArrayNode)mapper.readTree("[{\"code\":\"CMPSC 465\"}]"));
  var result=tools.call("check_prerequisites",args("{\"codes\":[\"CMPSC 465\",\"STAT 999\",\"not a code\"]}"),turn());
  assertTrue(result.path("ok").asBoolean());var r=result.at("/data/results");assertEquals(3,r.size());
  assertEquals("met",r.get(0).path("status").asText());assertEquals("NOT_FOUND",r.get(1).at("/error/code").asText());assertEquals("INVALID_ARGUMENT",r.get(2).at("/error/code").asText());
  assertEquals(1,result.path("warnings").toString().split("Enrollment restrictions",-1).length-1,"shared warnings appear once");
 }
}
