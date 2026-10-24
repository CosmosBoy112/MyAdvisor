package dev.lionpathway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.LinkedHashMap;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic multi-page extraction regression; never uploads real student records. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LONG_REPORT_TEST", matches="1")
class LiveLongReportTest {
 @Test void extractsAllGroupsFromEightPagesWithinRequestDeadline() throws Exception {
  var env=Dotenv.configure().ignoreIfMissing().load();
  var ai=new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),new ObjectMapper());
  var pages=new LinkedHashMap<Integer,String>();
  for(int page=1;page<=8;page++){
   var text=new StringBuilder("SYNTHETIC Academic Requirements / What-If Report. Not a real student record.\nMajor: Computer Science B.S.; Campus: University Park; Requirement year: 2026. No minor.\n");
   if(page==1)text.append("Overall degree totals: 120 required, 72 completed, 15 in progress.\n");
   for(int group=(page-1)*3+1;group<=page*3;group++){
    text.append("Requirement Group ").append(group).append(": 3 credits required; 0 completed; 0 in progress. Status: Not satisfied.\nSelect one 3-credit course from TEST ").append(100+group).append(" OR TEST ").append(200+group).append(". These options are synthetic and belong only to this group.\n");
   }
   pages.put(page,text.toString());
  }
  long started=System.nanoTime();
  var report=new AnalysisValidator().validate(ai.analyze(pages,"Explain my remaining requirements"),pages);
  assertEquals(120,report.path("totals").path("required").asInt());
  assertEquals(72,report.path("totals").path("completed").asInt());
  assertEquals(15,report.path("totals").path("inProgress").asInt());
  assertEquals(24,report.path("requirements").size());
  for(var requirement:report.path("requirements"))assertTrue(requirement.path("evidence").path("verified").asBoolean(),"Each source quote must match its page");
  System.out.println("LIVE_LONG_REPORT: 8 pages / 24 requirements passed in "+(System.nanoTime()-started)/1_000_000+" ms");
 }
}
