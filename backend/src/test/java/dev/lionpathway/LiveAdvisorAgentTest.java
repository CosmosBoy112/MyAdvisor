package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in: read-only catalog queries behind the agent tools, then one real tool-calling chat. Synthetic report only. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveAdvisorAgentTest {
 private static final String REPORT="""
  {"major":"Data Sciences - Computational Option (BS)","campus":"University Park","catalogYear":"2026-27","responseLanguage":"en","webSearchEnabled":false,
   "totals":{"required":123,"completed":64,"inProgress":15},
   "requirements":[{"title":"Prescribed Courses","status":"in_progress","required":40,"completed":28,"inProgress":6}],
   "courseInventory":{"method":"pdf_course_rows_v1","courseCount":3,"unparsedRowCount":0,"courses":[
    {"code":"CMPSC 360","term":"FA 2025","grade":"B","credits":3,"status":"recorded","requiresReview":false},
    {"code":"CMPSC 221","term":"FA 2025","grade":"A-","credits":3,"status":"recorded","requiresReview":false},
    {"code":"MATH 230","term":"SP 2026","grade":"IP","credits":4,"status":"in_progress","requiresReview":false}]}}""";
 @Test void toolsReadTheCatalogAndTheAgentCallsThem()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  try(var ds=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var catalog=new CatalogDbService(new JdbcTemplate(ds),mapper);var report=mapper.readTree(REPORT);
   var tools=new AdvisorTools(catalog,null,null,mapper);var turn=tools.turn(report,List.of());
   assertEquals("COMPUTATIONAL",turn.scope.orElseThrow().option());
   var search=tools.call("search_courses",mapper.readTree("{\"query\":\"algorithms\",\"subject\":\"CMPSC\"}"),turn);
   assertTrue(search.path("ok").asBoolean(),search.toString());assertTrue(search.at("/data/courses").size()>0,"search found no CMPSC algorithms course");
   var course=tools.call("get_course",mapper.readTree("{\"code\":\"CMPSC 465\"}"),turn);assertTrue(course.path("ok").asBoolean(),course.toString());
   var prereq=tools.call("check_prerequisites",mapper.readTree("{\"code\":\"CMPSC 465\"}"),turn);
   assertTrue(prereq.path("ok").asBoolean(),prereq.toString());assertTrue(Set.of("met","in_progress","not_met","unknown").contains(prereq.at("/data/status").asText()));
   var tree=tools.call("get_program_requirements",mapper.readTree("{}"),turn);assertTrue(tree.path("ok").asBoolean(),tree.toString());
   long pool=firstNodeWithCourses(tree.at("/data/requirements"));assertTrue(pool>0,"no requirement node with course rules");
   var options=tools.call("find_courses_for_requirement",mapper.readTree("{\"node_id\":"+pool+"}"),turn);
   assertTrue(options.path("ok").asBoolean(),options.toString());assertTrue(options.at("/data/courses").size()>0);
   var plan=tools.call("get_policies",mapper.readTree("{\"key\":\"suggested_academic_plan.COMPUTATIONAL\"}"),turn);assertTrue(plan.path("ok").asBoolean(),plan.toString());
   System.out.println("LIVE_CHECK: tools ok; search="+search.at("/data/courses").size()+" prereq="+prereq.at("/data/status").asText()+" node="+pool+" options="+options.at("/data/courses").size());

   var agent=new AdvisorAgent(new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper),tools,mapper,true);
   long started=System.nanoTime();var result=agent.chat(report,"Can I take CMPSC 465 next semester?",List.of());
   var names=new ArrayList<String>();result.toolCalls().forEach(c->names.add(c.path("name").asText()));
   assertFalse(result.text().isBlank());assertTrue(names.contains("check_prerequisites"),"agent did not check prerequisites: "+names);
   System.out.println("LIVE_CHECK: agent tools="+names+" elapsedMs="+(System.nanoTime()-started)/1_000_000+"\n"+result.text());

   var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
   if(!luminal.configured()){System.out.println("LIVE_CHECK: LUMINAL_API_KEY not set; Luminal agent skipped");return;}
   var luminalAgent=new AdvisorAgent(new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper),luminal,tools,mapper,true,"luminal");
   for(String question:List.of("Can I take CMPSC 465 next semester?","What algorithms course should I take?")){
    started=System.nanoTime();var answer=luminalAgent.chat(report,question,List.of());
    var used=new ArrayList<String>();answer.toolCalls().forEach(c->used.add(c.path("name").asText()+c.path("args")));
    System.out.println("LIVE_CHECK: question="+question+" provider="+answer.provider()+" tools="+used+" elapsedMs="+(System.nanoTime()-started)/1_000_000+"\n"+answer.text());
    assertFalse(answer.text().isBlank());
    if(question.contains("465")){assertEquals("luminal-agent",answer.provider(),"Luminal agent failed and Gemini answered");assertTrue(used.toString().contains("check_prerequisites"),"Luminal agent did not check prerequisites: "+used);}
   }
  }
 }
 /** Screens, memory and the run log end to end with the Luminal agent; every row it writes is rolled back. */
 @Test void agentOpensScreensSavesMemoriesAndLogsRuns()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  try(var pool=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"));var connection=pool.getConnection()){
   connection.setAutoCommit(false);
   try{
    org.springframework.jdbc.datasource.init.ScriptUtils.executeSqlScript(connection,new org.springframework.core.io.ClassPathResource("schema.sql"));
    var jdbc=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(connection,true));var store=new AdvisorStore(jdbc,mapper);
    String subject="live-test|"+UUID.randomUUID();var reportId=UUID.randomUUID();var report=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(REPORT);
    jdbc.update("INSERT INTO app_users(subject) VALUES (?)",subject);jdbc.update("INSERT INTO reports(id,owner_subject,filename,analysis) VALUES (?,?,?,?::jsonb)",reportId,subject,"synthetic.pdf",REPORT);
    var tools=new AdvisorTools(new CatalogDbService(jdbc,mapper),new MathMinorService(mapper,Optional.of(jdbc)),null,store,mapper);
    var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
    var agent=new AdvisorAgent(new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper),luminal,tools,store,mapper,true,"luminal");
    var views=new ArrayList<String>();var history=new ArrayList<Map<String,Object>>();
    for(String question:List.of("Can I take CMPSC 465 next semester?","Show me a semester plan to graduate.","Please remember that I want to avoid 8 am classes.","Computational Data Sciences, considering a Math minor")){
     long started=System.nanoTime();var answer=agent.chat(report,question,history,subject,reportId);
     var used=new ArrayList<String>();answer.toolCalls().forEach(c->used.add(c.path("name").asText()));
     String view=answer.view()==null?"none":answer.view().path("type").asText();views.add(view);
     System.out.println("LIVE_CHECK: question="+question+" provider="+answer.provider()+" tools="+used+" view="+view+" memory="+answer.memoryChanges()+" elapsedMs="+(System.nanoTime()-started)/1_000_000);
     if(answer.view()!=null)System.out.println("LIVE_VIEW: "+answer.view().toString().substring(0,Math.min(600,answer.view().toString().length())));
     System.out.println(answer.text());
     assertFalse(answer.text().isBlank());
     history.add(Map.of("role","user","content",question));history.add(Map.of("role","assistant","content",answer.text()));
    }
    var memories=store.memories(subject);System.out.println("LIVE_CHECK: memories="+memories);
    assertTrue(memories.stream().anyMatch(m->String.valueOf(m.get("content")).contains("8")),"agent did not save the stated preference");
    assertTrue(views.stream().anyMatch(v->!v.equals("none")),"agent opened no screen: "+views);
    var runs=jdbc.queryForList("SELECT model, status, llm_calls, tool_calls, view_type, latency_ms FROM agent_runs WHERE subject=? ORDER BY started_at",subject);
    System.out.println("LIVE_CHECK: runs="+runs);assertTrue(runs.size()>=4);
    assertTrue(jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE subject=? AND kind='tool'",Integer.class,subject)>0);
   }finally{connection.rollback();}
  }
 }
 private static long firstNodeWithCourses(JsonNode nodes){
  for(var n:nodes){if(n.path("course_count").asInt()>0&&n.has("id"))return n.path("id").asLong();long child=firstNodeWithCourses(n.path("children"));if(child>0)return child;}
  return 0;
 }
}
