package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.*;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class ReportOwnershipTest {
 @Test void reportsAreAlwaysSelectedByIdAndAuthenticatedSubject(){
  JdbcTemplate jdbc=mock(JdbcTemplate.class);
  UUID id=UUID.randomUUID();
  when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<String>>any(),eq(id),eq("auth0|different-user"))).thenReturn(List.of());
  var repository=new ReportRepository(jdbc,new ObjectMapper());
  assertThrows(ResponseStatusException.class,()->repository.get(id,"auth0|different-user"));
  verify(jdbc).query(eq("SELECT analysis::text FROM reports WHERE id=? AND owner_subject=?"),org.mockito.ArgumentMatchers.<RowMapper<String>>any(),eq(id),eq("auth0|different-user"));
 }
}
