package dev.lionpathway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in integration check. Synthetic data only; all test records are rolled back. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveServicesTest {
 @Test void syntheticReportCanBeAnalyzedSavedAndExplained() throws Exception {
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  var ai=new GeminiService(env.get("GEMINI_API_KEY"),env.get("GEMINI_MODEL"),mapper);
  var pages=Map.of(1,"""
    SYNTHETIC TEST FIXTURE - NO REAL STUDENT DATA
    Academic Requirements Report / What-If Report
    Major: Computer Science, B.S. Campus: University Park. Requirement year: 2026.
    Total degree credits required: 120. Total earned credits: 72. In-progress credits: 15.
    Department List: 12 credits required; 6 earned; 0 in progress. Status: Not satisfied.
    Choose 6 additional credits from the approved department list. Course options are not included.
    Supportive Electives: 9 credits required; 3 earned; 3 in progress. Status: Not satisfied.
    Select 3 additional credits after the in-progress course is completed.
    No minor is included in this report.
    """);
  var report=new AnalysisValidator().validate(ai.analyze(pages,"Computer Science; considering a Mathematics minor"),pages);
  assertEquals(120,report.path("totals").path("required").asInt());assertEquals(72,report.path("totals").path("completed").asInt());assertEquals(15,report.path("totals").path("inProgress").asInt());assertTrue(report.path("requirements").size()>=2);
  System.out.println("LIVE_CHECK: Gemini structured analysis passed (synthetic data only)");
  var answer=ai.chat(report,"How many credits remain beyond my current courses?",List.of());assertFalse(answer.isBlank());
  var config=new DatabaseConfig();
  try(var ds=config.dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var jdbc=config.jdbc(ds);var repository=new ReportRepository(jdbc,mapper);var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));
   tx.executeWithoutResult(status->{try{
    var id=UUID.randomUUID();String owner="integration-test:"+UUID.randomUUID();report.put("id",id.toString());report.put("demo",false);report.put("fileName","synthetic-test.pdf");
    repository.save(id,owner,"synthetic-test.pdf",report);assertEquals(120,repository.get(id,owner).path("totals").path("required").asInt());assertEquals(1,repository.list(owner).size());assertThrows(org.springframework.web.server.ResponseStatusException.class,()->repository.get(id,owner+"-other"));
    repository.conversation(id,"How many credits remain?",answer);assertEquals(2,repository.history(id).size());
    System.out.println("LIVE_CHECK: TigerData save/read, ownership isolation, and Gemini chat passed");
   }finally{status.setRollbackOnly();}});
   System.out.println("LIVE_CHECK: synthetic database records rolled back");
  }
 }
}
