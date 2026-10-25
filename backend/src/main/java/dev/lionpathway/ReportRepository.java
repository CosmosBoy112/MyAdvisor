package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
@Repository @Profile("live") public class ReportRepository {
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public ReportRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
 @Transactional public void save(UUID id,String owner,String filename,JsonNode report){jdbc.update("INSERT INTO app_users(subject) VALUES (?) ON CONFLICT DO NOTHING",owner);jdbc.update("INSERT INTO reports(id,owner_subject,filename,analysis) VALUES (?,?,?,?::jsonb)",id,owner,filename,report.toString());}
 public JsonNode get(UUID id,String owner){var rows=jdbc.query("SELECT analysis::text FROM reports WHERE id=? AND owner_subject=?",(rs,n)->rs.getString(1),id,owner);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Report not found");try{return mapper.readTree(rows.getFirst());}catch(Exception e){throw new IllegalStateException("Stored report could not be read");}}
 public List<Map<String,Object>> list(String owner){return jdbc.queryForList("SELECT id,filename,analysis->>'major' AS major,created_at AS \"createdAt\" FROM reports WHERE owner_subject=? ORDER BY created_at DESC LIMIT 50",owner);}
 public List<Map<String,Object>> history(UUID id){var rows=jdbc.queryForList("SELECT role, content FROM (SELECT id,role,content FROM chat_messages WHERE report_id=? ORDER BY id DESC LIMIT 12) recent ORDER BY id",id);return rows.stream().map(ChatMessageCodec::decode).toList();}
 @Transactional public void conversation(UUID id,String question,String answer){jdbc.update("INSERT INTO chat_messages(report_id,role,content) VALUES (?,'user',?),(?,'assistant',?)",id,question,id,answer);}
 public void delete(UUID id,String owner){jdbc.update("DELETE FROM reports WHERE id=? AND owner_subject=?",id,owner);}
}
