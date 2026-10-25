package dev.lionpathway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in dry run: schema.sql (with the agent log and memory tables) and AdvisorStore inside one transaction that is rolled back. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveAdvisorStoreTest {
 @Test void schemaAndStoreWorkThenRollBack()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();
  try(var pool=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"));var connection=pool.getConnection()){
   connection.setAutoCommit(false);
   try{
    ScriptUtils.executeSqlScript(connection,new ClassPathResource("schema.sql"));
    var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));var mapper=new ObjectMapper();var store=new AdvisorStore(jdbc,mapper);
    String subject="live-test|"+UUID.randomUUID();var report=UUID.randomUUID();
    jdbc.update("INSERT INTO app_users(subject) VALUES (?)",subject);
    jdbc.update("INSERT INTO reports(id,owner_subject,filename,analysis) VALUES (?,?,?,'{}'::jsonb)",report,subject,"synthetic.pdf");
    var saved=store.saveMemory(subject,report,"preference","Avoid 8 am classes",null);
    assertEquals(1,store.memories(subject).size());assertFalse(store.forgetMemory("someone-else",(Long)saved.get("id")),"memories are scoped to their owner");
    var run=UUID.randomUUID();
    store.saveRun(run,Instant.now(),subject,report,"luminal-agent:test","ok",1234,2,1,"course_detail",null,List.of(
     new AdvisorStore.Step("llm",null,null,mapper.createObjectNode().put("toolCalls",1),900,null),
     new AdvisorStore.Step("tool","get_course",mapper.createObjectNode().put("code","CMPSC 465"),mapper.createObjectNode().put("ok",true),30,null)));
    assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE id=?",Integer.class,run));
    assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM agent_steps WHERE run_id=?",Integer.class,run));
    assertEquals("CMPSC 465",jdbc.queryForObject("SELECT arguments->>'code' FROM agent_steps WHERE run_id=? AND kind='tool'",String.class,run));
    jdbc.update("DELETE FROM reports WHERE id=?",report);
    assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM agent_runs WHERE id=?",Integer.class,run),"logs are deleted with their report");
    assertEquals(1,store.memories(subject).size(),"memories outlive a deleted report");
    System.out.println("LIVE_CHECK: schema + AdvisorStore dry run passed (rolled back)");
   }finally{connection.rollback();}
  }
 }
}
