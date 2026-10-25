package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReportDeletionTest {
 @Test void lastReportIsRejectedWithoutDeletingAnything(){
  var jdbc=mock(JdbcTemplate.class);var id=UUID.randomUUID();
  when(jdbc.queryForList(anyString(),eq(String.class),eq("owner"))).thenReturn(List.of("owner"));
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq(id),eq("owner"))).thenReturn(1L);
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq("owner"))).thenReturn(1L);
  var error=assertThrows(ResponseStatusException.class,()->new ReportRepository(jdbc,new ObjectMapper()).delete(id,"owner"));
  assertEquals(409,error.getStatusCode().value());verify(jdbc,never()).update(anyString(),any(),any());
 }
 @Test void multipleReportsAllowOwnedDeletionUnderAccountLock()throws Exception{
  var jdbc=mock(JdbcTemplate.class);var id=UUID.randomUUID();
  when(jdbc.queryForList(anyString(),eq(String.class),eq("owner"))).thenReturn(List.of("owner"));
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq(id),eq("owner"))).thenReturn(1L);
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq("owner"))).thenReturn(2L);
  new ReportRepository(jdbc,new ObjectMapper()).delete(id,"owner");
  var order=inOrder(jdbc);order.verify(jdbc).queryForList(contains("FOR UPDATE"),eq(String.class),eq("owner"));
  order.verify(jdbc).queryForObject(contains("id=? AND owner_subject=?"),eq(Long.class),eq(id),eq("owner"));
  order.verify(jdbc).queryForObject(contains("owner_subject=?"),eq(Long.class),eq("owner"));
  order.verify(jdbc).update("DELETE FROM reports WHERE id=? AND owner_subject=?",id,"owner");
  assertNotNull(ReportRepository.class.getMethod("delete",UUID.class,String.class).getAnnotation(Transactional.class));
 }
 @Test void someoneElsesReportIsNeverDeletedOrCountedAsAnOwnedReport(){
  var jdbc=mock(JdbcTemplate.class);var id=UUID.randomUUID();
  when(jdbc.queryForList(anyString(),eq(String.class),eq("other"))).thenReturn(List.of("other"));
  when(jdbc.queryForObject(anyString(),eq(Long.class),eq(id),eq("other"))).thenReturn(0L);
  new ReportRepository(jdbc,new ObjectMapper()).delete(id,"other");
  verify(jdbc,never()).queryForObject(anyString(),eq(Long.class),eq("other"));verify(jdbc,never()).update(anyString(),any(),any());
 }
}
