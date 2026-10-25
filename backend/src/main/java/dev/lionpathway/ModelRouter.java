package dev.lionpathway;
import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
// Simple lookups go to Gemini; multi-step planning goes to Luminal when configured, falling back to Gemini on failure.
@Service @Profile("live") public class ModelRouter {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ModelRouter.class);
 static final Pattern COMPLEX=Pattern.compile("(?iu)\\b(plan|planning|schedule|semesters?|roadmap|what[- ]?if|compare|comparison|sequence|order of|four[- ]year|double major)\\b|계획|학기별|로드맵|비교|순서|시뮬레이션|복수전공|언제 졸업");
 private final GeminiService gemini;private final LuminalService luminal;private final WebResearchService web;
 public ModelRouter(GeminiService gemini,LuminalService luminal){this(gemini,luminal,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ModelRouter(GeminiService gemini,LuminalService luminal,WebResearchService web){this.gemini=gemini;this.luminal=luminal;this.web=web;}
 static boolean complex(String question){return question.length()>400||COMPLEX.matcher(question).find();}
 public Answer chat(JsonNode report,String question,List<Map<String,Object>> history){
  var withResearch=withResearch(report,question,history);
  var raw=answer(withResearch.report(),question,history);
  return new Answer(raw.text(),raw.provider(),withResearch.research());
 }
 /** Fixed-context chat after an agent failure: same web evidence, Gemini only (at most 30 s search + 40 s answer). */
 public Answer fallbackChat(JsonNode report,String question,List<Map<String,Object>> history){
  var withResearch=withResearch(report,question,history);
  return new Answer(gemini.chat(withResearch.report(),question,history),"gemini",withResearch.research());
 }
 private record Researched(JsonNode report,JsonNode research){}
 private Researched withResearch(JsonNode report,String question,List<Map<String,Object>> history){
  if(web==null || !WebResearchService.shouldSearch(report,question))return new Researched(report,null);
  var research=web.search(question,history);
  var copy=(com.fasterxml.jackson.databind.node.ObjectNode)report.deepCopy();
  var context=research.deepCopy();((com.fasterxml.jackson.databind.node.ObjectNode)context).remove("searchEntryPoint");
  copy.set("webResearch",context);return new Researched(copy,research);
 }
 private Answer answer(JsonNode report,String question,List<Map<String,Object>> history){
  if(complex(question)&&luminal.configured()){
   try{return new Answer(luminal.chat(report,question,history),"luminal");}
   catch(UpstreamFailure | AnalysisTimeout e){log.warn("Luminal failed={}, falling back to Gemini",e.getClass().getSimpleName());}
  }
  try{return new Answer(gemini.chat(report,question,history),"gemini");}
  catch(UpstreamFailure | AnalysisTimeout e){
   if(!complex(question)&&luminal.configured())return new Answer(luminal.chat(report,question,history),"luminal");
   throw e;
  }
 }
 public record Answer(String text,String provider,JsonNode webResearch){public Answer(String text,String provider){this(text,provider,null);}}
}
