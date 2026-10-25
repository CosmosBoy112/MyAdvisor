package dev.lionpathway;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
/**
 * In-memory progress of chat answers still being written, so the frontend can poll "checking prerequisites…" while it waits.
 * Keyed by the JWT subject and a client request id; nothing is stored beyond a few minutes and no tool data is kept.
 */
@Component @Profile("live")
public class AgentProgress {
 static final long KEEP_MS=10*60_000;static final int MAX_STEPS=40,MAX_ENTRIES=2000;
 record Step(String kind,String detail,long at){}
 static final class Entry {final List<Step> steps=Collections.synchronizedList(new ArrayList<>());volatile boolean done;volatile long touched=System.currentTimeMillis();}
 private final Map<String,Entry> entries=new ConcurrentHashMap<>();

 /** Receives agent steps: kind is llm / tool / fallback / retry, detail a tool name or provider. */
 @FunctionalInterface public interface Listener{void step(String kind,String detail);static Listener none(){return (k,d)->{};}}

 Listener start(String subject,String requestId){
  prune();var entry=new Entry();entries.put(key(subject,requestId),entry);
  return (kind,detail)->{if(entry.steps.size()<MAX_STEPS)entry.steps.add(new Step(kind,detail,System.currentTimeMillis()));entry.touched=System.currentTimeMillis();};
 }
 void finish(String subject,String requestId){var e=entries.get(key(subject,requestId));if(e!=null){e.done=true;e.touched=System.currentTimeMillis();}}
 /** Only the subject that started the request can read it. */
 Optional<Map<String,Object>> read(String subject,String requestId){
  var e=entries.get(key(subject,requestId));if(e==null)return Optional.empty();
  List<Map<String,Object>> steps;synchronized(e.steps){steps=e.steps.stream().map(s->Map.<String,Object>of("kind",s.kind(),"detail",s.detail()==null?"":s.detail(),"at",s.at())).toList();}
  return Optional.of(Map.of("steps",steps,"done",e.done));
 }
 private static String key(String subject,String requestId){return subject+"\n"+requestId;}
 private void prune(){
  long cutoff=System.currentTimeMillis()-KEEP_MS;entries.values().removeIf(e->e.touched<cutoff);
  if(entries.size()>MAX_ENTRIES)entries.clear();
 }
}
