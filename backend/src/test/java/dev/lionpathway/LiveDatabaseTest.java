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
   var report=mapper.createObjectNode();report.put("major","Synthetic integration test");
   new TransactionTemplate(new DataSourceTransactionManager(ds)).executeWithoutResult(status->{try{
    var id=UUID.randomUUID();var owner="integration-test:"+UUID.randomUUID();
    repository.save(id,owner,"synthetic-test.pdf",report);assertEquals(report,repository.get(id,owner));assertEquals(1,repository.list(owner).size());
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->repository.get(id,owner+"other"));
    repository.conversation(id,"Synthetic question","Synthetic answer");assertEquals(2,repository.history(id).size());
    repository.delete(id,owner+"other");assertEquals(report,repository.get(id,owner));
    repository.delete(id,owner);assertTrue(repository.history(id).isEmpty());assertTrue(repository.list(owner).isEmpty());
    System.out.println("LIVE_CHECK: TigerData CRUD, chat history, ownership and cascade deletion passed; rollback scheduled");
   }finally{status.setRollbackOnly();}});
  }
 }
}
