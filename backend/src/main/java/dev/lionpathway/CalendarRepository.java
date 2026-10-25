package dev.lionpathway;
import java.time.LocalDate;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
/** Registrar academic calendar (terms, academic_calendar), loaded by ingest/load_calendar_db.py. Read-only. */
@Repository @Profile("live")
public class CalendarRepository {
 private final JdbcTemplate jdbc;
 public CalendarRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 record Term(String code,LocalDate startsOn,LocalDate endsOn){}
 record Event(String term,String key,String label,LocalDate startsOn,LocalDate endsOn,String campus,String sourceUrl,java.time.Instant fetchedAt){}

 Optional<Term> term(String code){return jdbc.query("SELECT code, starts_on, ends_on FROM terms WHERE code=?",(rs,n)->new Term(rs.getString(1),rs.getDate(2).toLocalDate(),rs.getDate(3).toLocalDate()),code).stream().findFirst();}
 /** The term in session on that day, else the next one to start (between terms, the coming term). */
 Optional<Term> currentOrNext(LocalDate day){
  return jdbc.query("SELECT code, starts_on, ends_on FROM terms WHERE ends_on>=? ORDER BY starts_on LIMIT 1",(rs,n)->new Term(rs.getString(1),rs.getDate(2).toLocalDate(),rs.getDate(3).toLocalDate()),java.sql.Date.valueOf(day)).stream().findFirst();
 }
 List<String> termCodes(){return jdbc.queryForList("SELECT code FROM terms ORDER BY starts_on",String.class);}
 /** Events of one term, optionally only the given keys; campus keeps rows for all campuses (null) plus that campus. */
 List<Event> events(String term,List<String> keys,String campus){
  var sql=new StringBuilder("SELECT term_code, event_key, label, starts_on, ends_on, campus, source_url, fetched_at FROM academic_calendar WHERE term_code=?");
  var args=new ArrayList<Object>(List.of(term));
  if(!keys.isEmpty()){sql.append(" AND event_key IN (").append(String.join(",",Collections.nCopies(keys.size(),"?"))).append(")");args.addAll(keys);}
  if(campus!=null){sql.append(" AND (campus IS NULL OR campus=?)");args.add(campus);}
  sql.append(" ORDER BY ends_on, event_key");
  return jdbc.query(sql.toString(),(rs,n)->new Event(rs.getString(1),rs.getString(2),rs.getString(3),rs.getDate(4)==null?null:rs.getDate(4).toLocalDate(),rs.getDate(5).toLocalDate(),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),args.toArray());
 }
 /** Every event whose deadline falls in [from, to], across terms, for the "upcoming deadlines" context. */
 List<Event> between(LocalDate from,LocalDate to,String campus){
  return jdbc.query("SELECT term_code, event_key, label, starts_on, ends_on, campus, source_url, fetched_at FROM academic_calendar WHERE ends_on BETWEEN ? AND ? AND (campus IS NULL OR campus=?) ORDER BY ends_on LIMIT 8",
   (rs,n)->new Event(rs.getString(1),rs.getString(2),rs.getString(3),rs.getDate(4)==null?null:rs.getDate(4).toLocalDate(),rs.getDate(5).toLocalDate(),rs.getString(6),rs.getString(7),rs.getTimestamp(8).toInstant()),
   java.sql.Date.valueOf(from),java.sql.Date.valueOf(to),campus==null?"UP":campus);
 }
}
