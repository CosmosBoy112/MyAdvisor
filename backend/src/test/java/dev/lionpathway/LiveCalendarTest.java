package dev.lionpathway;
import io.github.cdimascio.dotenv.Dotenv;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Opt-in, read-only: the calendar queries run against TigerData. Before ingest/load_calendar_db.py runs there are no rows. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveCalendarTest {
 @Test void calendarQueriesRun()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();
  try(var ds=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var calendar=new CalendarRepository(new JdbcTemplate(ds));
   var terms=calendar.termCodes();var today=LocalDate.of(2026,10,24);
   var current=calendar.currentOrNext(today);
   var events=current.map(t->calendar.events(t.code(),List.of("late_drop","withdrawal"),"UP")).orElse(List.of());
   var soon=calendar.between(today,today.plusDays(30),"UP");
   System.out.println("LIVE_CHECK: terms="+terms+" current="+current.map(CalendarRepository.Term::code).orElse("none")+" events="+events.size()+" next30days="+soon.size());
   if(!terms.isEmpty()){assertTrue(current.isPresent());assertFalse(events.isEmpty());}
  }
 }
}
