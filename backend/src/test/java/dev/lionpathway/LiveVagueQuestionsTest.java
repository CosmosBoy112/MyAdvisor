package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in: vague follow-up questions answered from the student profile (Luminal agent, Tavily). Synthetic report, read-only DB. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveVagueQuestionsTest {
 private static final String REPORT="""
  {"major":"Data Sciences - Computational Option (BS)","campus":"University Park","catalogYear":"2026-27","responseLanguage":"en","webSearchEnabled":true,
   "totals":{"required":123,"completed":64,"inProgress":15},
   "requirements":[{"title":"Prescribed Courses","status":"in_progress","required":40,"completed":28,"inProgress":6},{"title":"Computational Option List A","status":"in_progress","required":6,"completed":0,"inProgress":0},{"title":"General Education","status":"completed"}],
   "courseInventory":{"method":"pdf_course_rows_v1","courseCount":9,"courses":[
    {"code":"CMPSC 131","term":"FA 2024","grade":"A","credits":3,"status":"recorded","requiresReview":false},
    {"code":"CMPSC 132","term":"SP 2025","grade":"B+","credits":3,"status":"recorded","requiresReview":false},
    {"code":"MATH 140","term":"FA 2024","grade":"B","credits":4,"status":"recorded","requiresReview":false},
    {"code":"MATH 141","term":"SP 2025","grade":"B-","credits":4,"status":"recorded","requiresReview":false},
    {"code":"DS 200","term":"FA 2024","grade":"A-","credits":4,"status":"recorded","requiresReview":false},
    {"code":"DS 220","term":"SP 2025","grade":"A","credits":3,"status":"recorded","requiresReview":false},
    {"code":"CMPSC 221","term":"FA 2025","grade":"A-","credits":3,"status":"recorded","requiresReview":false},
    {"code":"CMPSC 360","term":"FA 2025","grade":"B","credits":3,"status":"recorded","requiresReview":false},
    {"code":"MATH 230","term":"SP 2026","grade":"IP","credits":4,"status":"in_progress","requiresReview":false}]}}""";
 @Test void vagueFollowUpsUseTheProfile()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  try(var ds=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var jdbc=new JdbcTemplate(ds);var report=mapper.readTree(REPORT);
   var web=new WebResearchService(env.get("GEMINI_API_KEY",""),env.get("GEMINI_MODEL"),"tavily",env.get("TAVILY_API_KEY",""),mapper);
   var tools=new AdvisorTools(new CatalogDbService(jdbc,mapper),new MathMinorService(mapper,Optional.of(jdbc)),web,mapper);
   var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
   var agent=new AdvisorAgent(new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper),luminal,tools,mapper,true,"luminal");
   var history=new ArrayList<Map<String,Object>>();
   for(String q:List.of("what should I take next semester?","is it hard?","any good electives?")){
    long started=System.nanoTime();var a=agent.chat(report,q,history);
    var used=new ArrayList<String>();a.toolCalls().forEach(c->used.add(c.path("name").asText()+c.path("args")));
    System.out.println("LIVE_CHECK: q="+q+" provider="+a.provider()+" view="+(a.view()==null?"none":a.view().path("type").asText())+" elapsedMs="+(System.nanoTime()-started)/1_000_000+"\n tools="+used+"\n"+a.text()+"\n---");
    assertFalse(a.text().isBlank());
    history.add(Map.of("role","user","content",q));var reply=new HashMap<String,Object>(Map.of("role","assistant","content",a.text()));if(a.view()!=null)reply.put("view",a.view());history.add(reply);
   }
  }
 }
}
