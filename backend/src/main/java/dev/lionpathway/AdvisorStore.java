package dev.lionpathway;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
/** Agent run log (agent_runs / agent_steps) and the student's long-term memories. Every query is scoped to the JWT subject. */
@Repository @Profile("live") public class AdvisorStore {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AdvisorStore.class);
 static final int MAX_MEMORIES=50,MAX_LOGGED_RESULT=20_000;
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public AdvisorStore(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

 record Step(String kind,String toolName,JsonNode arguments,JsonNode result,long latencyMs,String errorCode){}
 /** Never throws: a logging failure must not lose the student's answer. */
 public void saveRun(UUID runId,Instant started,String subject,UUID reportId,String model,String status,long latencyMs,int llmCalls,int toolCalls,String viewType,String errorCode,List<Step> steps){
  try{
   jdbc.update("INSERT INTO agent_runs(id,started_at,subject,report_id,model,status,latency_ms,llm_calls,tool_calls,view_type,error_code) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
    runId,Timestamp.from(started),subject,reportId,model,status,(int)latencyMs,llmCalls,toolCalls,viewType,errorCode);
   int n=0;for(var s:steps){n++;
    jdbc.update("INSERT INTO agent_steps(run_id,step_no,created_at,subject,report_id,kind,tool_name,arguments,result,latency_ms,error_code) VALUES (?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?)",
     runId,n,Timestamp.from(started),subject,reportId,s.kind(),s.toolName(),json(s.arguments()),json(s.result()),(int)s.latencyMs(),s.errorCode());
   }
  }catch(RuntimeException e){log.warn("Agent run log failed={}",e.getClass().getSimpleName());}
 }
 // Large tool results (requirement trees) are summarized so the log stays small.
 private String json(JsonNode node){if(node==null)return null;String text=node.toString();return text.length()<=MAX_LOGGED_RESULT?text:mapper.createObjectNode().put("truncated",true).put("chars",text.length()).toString();}

 public List<Map<String,Object>> memories(String subject){return jdbc.queryForList("SELECT id, category, content FROM student_memories WHERE subject=? ORDER BY id",subject);}
 @Transactional public Map<String,Object> saveMemory(String subject,UUID reportId,String category,String content,Long replacesId){
  if(replacesId!=null){
   int updated=jdbc.update("UPDATE student_memories SET category=?, content=?, report_id=?, updated_at=now() WHERE id=? AND subject=?",category,content,reportId,replacesId,subject);
   if(updated==0)throw new IllegalArgumentException("Memory "+replacesId+" does not exist.");
   return Map.of("id",replacesId,"category",category,"content",content);
  }
  Integer count=jdbc.queryForObject("SELECT count(*) FROM student_memories WHERE subject=?",Integer.class,subject);
  if(count!=null&&count>=MAX_MEMORIES)throw new IllegalArgumentException("The student already has "+MAX_MEMORIES+" memories; ask which to forget first.");
  jdbc.update("INSERT INTO app_users(subject) VALUES (?) ON CONFLICT DO NOTHING",subject);
  Long id=jdbc.queryForObject("INSERT INTO student_memories(subject,category,content,report_id) VALUES (?,?,?,?) RETURNING id",Long.class,subject,category,content,reportId);
  return Map.of("id",id,"category",category,"content",content);
 }
 public boolean forgetMemory(String subject,long id){return jdbc.update("DELETE FROM student_memories WHERE id=? AND subject=?",id,subject)>0;}
}
