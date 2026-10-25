package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in: one real Tavily search (1 credit), then one Luminal agent answer that needs the web. Synthetic report, read-only DB. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveTavilyTest {
 @Test void tavilyFeedsTheAgentsWebSearch()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  var web=new WebResearchService(env.get("GEMINI_API_KEY",""),env.get("GEMINI_MODEL"),"tavily",env.get("TAVILY_API_KEY",""),mapper);
  assertTrue(web.usesTavily(),"TAVILY_API_KEY is not set in backend/.env");
  long started=System.nanoTime();var found=web.search("CMPSC 465 workload and difficulty",List.of());
  System.out.println("LIVE_CHECK: tavily status="+found.path("status").asText()+" provider="+found.path("provider").asText()+" sources="+found.path("sources").size()+" chars="+found.toString().length()+" elapsedMs="+(System.nanoTime()-started)/1_000_000);
  for(var s:found.path("sources"))System.out.println("  "+s.path("id").asText()+" "+s.path("kind").asText()+" "+s.path("url").asText());
  assertEquals("searched",found.path("status").asText());assertEquals("tavily",found.path("provider").asText());

  try(var ds=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var report=mapper.readTree("""
    {"major":"Data Sciences - Computational Option (BS)","campus":"University Park","catalogYear":"2026-27","responseLanguage":"en","webSearchEnabled":true,
     "totals":{"required":123,"completed":64,"inProgress":15},"requirements":[{"title":"Prescribed Courses","status":"in_progress"}],
     "courseInventory":{"method":"pdf_course_rows_v1","courseCount":1,"courses":[{"code":"CMPSC 360","term":"FA 2025","grade":"B","credits":3,"status":"recorded","requiresReview":false}]}}""");
   var tools=new AdvisorTools(new CatalogDbService(new JdbcTemplate(ds),mapper),null,web,mapper);
   var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
   var agent=new AdvisorAgent(new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper),luminal,tools,mapper,true,"luminal");
   started=System.nanoTime();var answer=agent.chat(report,"What do students on Reddit say about the CMPSC 465 workload?",List.of());
   var used=new ArrayList<String>();answer.toolCalls().forEach(c->used.add(c.path("name").asText()+c.path("args")));
   System.out.println("LIVE_CHECK: provider="+answer.provider()+" tools="+used+" web="+(answer.webResearch()==null?"none":answer.webResearch().path("provider").asText()+"/"+answer.webResearch().path("status").asText())+" elapsedMs="+(System.nanoTime()-started)/1_000_000+"\n"+answer.text());
   assertTrue(used.toString().contains("web_search"),"agent did not search the web: "+used);
   assertNotNull(answer.webResearch());assertEquals("tavily",answer.webResearch().path("provider").asText());
  }
 }
}
