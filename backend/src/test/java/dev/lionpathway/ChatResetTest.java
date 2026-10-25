package dev.lionpathway;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ChatResetTest {
 @Test void onlyOwnedConversationIsDeleted(){
  var jdbc=mock(JdbcTemplate.class);var id=UUID.randomUUID();
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq(id),eq("owner"))).thenReturn(1L);
  new ReportRepository(jdbc,new ObjectMapper()).clearHistory(id,"owner");
  verify(jdbc).update("DELETE FROM chat_messages WHERE report_id IN (SELECT id FROM reports WHERE id=? AND owner_subject=?)",id,"owner");
  verify(jdbc,never()).update(contains("DELETE FROM reports"),any(),any());
 }
 @Test void unownedReportCannotHaveItsMessagesCleared(){
  var jdbc=mock(JdbcTemplate.class);var id=UUID.randomUUID();
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq(id),eq("other"))).thenReturn(0L);
  assertEquals(404,assertThrows(ResponseStatusException.class,()->new ReportRepository(jdbc,new ObjectMapper()).clearHistory(id,"other")).getStatusCode().value());
  verify(jdbc,never()).update(anyString(),any(),any());
 }
}
