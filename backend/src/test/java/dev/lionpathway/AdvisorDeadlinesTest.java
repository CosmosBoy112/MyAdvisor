package dev.lionpathway;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AdvisorDeadlinesTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private final CalendarRepository calendar=mock(CalendarRepository.class);
 private final AdvisorTools tools=new AdvisorTools(mock(CatalogDbService.class),null,null,mapper);
 private static final String URL="https://www.registrar.psu.edu/academic-calendars/2026-27.cfm";
 private static final Instant FETCHED=Instant.parse("2026-10-24T04:00:00Z");
 private final CalendarRepository.Term fall=new CalendarRepository.Term("FA26",LocalDate.of(2026,8,24),LocalDate.of(2026,12,18));
 private AdvisorTools.Turn turn(String campus)throws Exception{return new AdvisorTools.Turn(mapper.readTree(campus==null?"{}":"{\"campus\":\""+campus+"\"}"),List.of(),Optional.empty(),false);}
 private CalendarRepository.Event event(String key,String label,LocalDate end,String campus){return new CalendarRepository.Event("FA26",key,label,null,end,campus,URL,FETCHED);}
 private void at(String day){tools.clock=Clock.fixed(LocalDate.parse(day).atStartOfDay(ZoneId.of("America/New_York")).toInstant(),ZoneId.of("America/New_York"));}

 @Test void defaultsToTheTermInSessionAndCountsDaysLeft()throws Exception{
  tools.setCalendar(calendar);at("2026-10-24");
  when(calendar.currentOrNext(LocalDate.of(2026,10,24))).thenReturn(Optional.of(fall));
  when(calendar.events("FA26",List.of("late_drop"),"UP")).thenReturn(List.of(event("late_drop","Late Drop - Deadline, 11:59 p.m. ET (full-semester courses)",LocalDate.of(2026,11,13),null)));
  var r=tools.call("get_deadlines",mapper.readTree("{\"event_keys\":[\"late_drop\"]}"),turn("University Park"));
  assertTrue(r.path("ok").asBoolean(),r.toString());
  assertEquals("FA26",r.at("/data/term").asText());assertEquals("UP",r.at("/data/campus").asText());
  assertEquals("2026-11-13",r.at("/data/events/0/ends_on").asText());assertEquals(20,r.at("/data/events/0/days_left").asInt());
  assertEquals(URL,r.at("/sources/0/url").asText());assertTrue(r.path("warnings").toString().contains("7-week"),"sections with their own deadlines are named");
  assertFalse(r.path("warnings").toString().contains("retrieved more than"));
 }
 @Test void explicitTermsUnknownTermsAndStaleDates()throws Exception{
  tools.setCalendar(calendar);at("2027-01-05");
  when(calendar.term("SP28")).thenReturn(Optional.empty());when(calendar.termCodes()).thenReturn(List.of("FA26","SP27","SU27"));
  var missing=tools.call("get_deadlines",mapper.readTree("{\"term_code\":\"sp28\"}"),turn(null));
  assertEquals("NOT_FOUND",missing.at("/error/code").asText());assertTrue(missing.at("/error/message").asText().contains("SP27"));
  when(calendar.term("FA26")).thenReturn(Optional.of(fall));
  when(calendar.events("FA26",List.of(),null)).thenReturn(List.of(event("withdrawal","Withdrawal - Deadline, 5:00 p.m. ET",LocalDate.of(2026,12,11),null)));
  var old=tools.call("get_deadlines",mapper.readTree("{\"term_code\":\"FA26\"}"),turn(null));
  assertEquals(-25,old.at("/data/events/0/days_left").asInt(),"past deadlines are negative, not hidden");
  assertTrue(old.path("warnings").toString().contains("retrieved more than 30 days ago"));
 }
 @Test void otherCampusesAreWarnedAndNoCalendarMeansNoTool()throws Exception{
  assertFalse(tools.declarations(turn(null)).toString().contains("get_deadlines"),"not offered before the calendar is wired");
  assertEquals("DATA_NOT_AVAILABLE",tools.call("get_deadlines",mapper.readTree("{}"),turn(null)).at("/error/code").asText());
  assertEquals(0,tools.upcomingDeadlines(mapper.readTree("{}")).size());
  tools.setCalendar(calendar);at("2026-10-24");assertTrue(tools.declarations(turn(null)).toString().contains("get_deadlines"));
  when(calendar.currentOrNext(any())).thenReturn(Optional.of(fall));when(calendar.events(eq("FA26"),any(),eq("ALTOONA"))).thenReturn(List.of());
  var r=tools.call("get_deadlines",mapper.readTree("{\"campus\":\"Altoona\"}"),turn("University Park"));
  assertTrue(r.path("warnings").toString().contains("vary by campus"));assertTrue(r.path("warnings").toString().contains("No calendar events"));
  assertEquals("UP",AdvisorTools.campusCode("UP"));assertEquals("UP",AdvisorTools.campusCode("University Park"));assertNull(AdvisorTools.campusCode(" "));
 }
 @Test void upcomingDeadlinesFeedTheAgentContext()throws Exception{
  tools.setCalendar(calendar);at("2026-11-05");
  when(calendar.between(LocalDate.of(2026,11,5),LocalDate.of(2026,11,19),"UP")).thenReturn(List.of(event("late_drop","Late Drop - Deadline",LocalDate.of(2026,11,13),null)));
  var report=mapper.readTree("{\"campus\":\"University Park\"}");
  var upcoming=tools.upcomingDeadlines(report);assertEquals(1,upcoming.size());assertEquals(8,upcoming.get(0).path("days_left").asInt());
  var agent=new AdvisorAgent(mock(GeminiService.class),tools,mapper,true);
  assertTrue(agent.input(report,new AdvisorTools.Turn(report,List.of(),Optional.empty(),false),"q",List.of()).contains("upcomingDeadlines"));
  when(calendar.between(any(),any(),any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
  assertEquals(0,tools.upcomingDeadlines(report).size(),"a calendar outage never blocks the answer");
 }
}
