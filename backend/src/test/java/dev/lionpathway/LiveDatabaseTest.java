package dev.lionpathway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveDatabaseTest {
 @Test void databaseStoresOwnedReportsAndConversation()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var config=new DatabaseConfig();
  try(var ds=config.dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var jdbc=config.jdbc(ds);var mapper=new ObjectMapper();var repository=new ReportRepository(jdbc,mapper);
   var report=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree("""
   {"major":"Computer Science, B.S.","campus":"University Park","catalogYear":"2026-27","totals":{"required":127},"requirements":[{"title":"Department List","required":6}]}
   """);
   new CatalogService(mapper).enrich(report,Map.of(1,"Computer Science University Park 2026-27"));
   new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(status->{try{
    var id=UUID.randomUUID();var owner="integration-test:"+UUID.randomUUID();
    repository.save(id,owner,"synthetic-test.pdf",report);assertEquals(report,repository.get(id,owner));assertEquals("matched_with_review",repository.get(id,owner).at("/catalog/status").asText());assertEquals(6,repository.get(id,owner).at("/requirements/0/catalogMatch/requiredCredits").asInt());assertEquals(1,repository.list(owner).size());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->repository.get(id,owner+"other"));
    repository.conversation(id,"Synthetic question","Synthetic answer");assertEquals(2,repository.history(id).size());
    repository.delete(id,owner+"other");assertEquals(report,repository.get(id,owner));
    var blocked=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->repository.delete(id,owner));assertEquals(409,blocked.getStatusCode().value());assertEquals(2,repository.history(id).size());
    var second=UUID.randomUUID();repository.save(second,owner,"second-synthetic.pdf",report);
    repository.delete(id,owner);assertTrue(repository.history(id).isEmpty());assertEquals(1,repository.list(owner).size());assertEquals(report,repository.get(second,owner));
    System.out.println("LIVE_CHECK: TigerData CRUD, chat history, ownership and cascade deletion passed; rollback scheduled");
   }finally{status.setRollbackOnly();}});
  }
 }
}
