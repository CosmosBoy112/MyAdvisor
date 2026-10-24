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
 private final GeminiService gemini;private final LuminalService luminal;
 public ModelRouter(GeminiService gemini,LuminalService luminal){this.gemini=gemini;this.luminal=luminal;}
 static boolean complex(String question){return question.length()>400||COMPLEX.matcher(question).find();}
 public Answer chat(JsonNode report,String question,List<Map<String,Object>> history){
  if(complex(question)&&luminal.configured()){
   try{return new Answer(luminal.chat(report,question,history),"luminal");}
   catch(UpstreamFailure | AnalysisTimeout e){log.warn("Luminal failed={}, falling back to Gemini",e.getClass().getSimpleName());}
  }
  return new Answer(gemini.chat(report,question,history),"gemini");
 }
 public record Answer(String text,String provider){}
}
