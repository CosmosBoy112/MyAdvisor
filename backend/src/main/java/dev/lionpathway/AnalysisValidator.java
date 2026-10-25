package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.stereotype.Component;
@Component public class AnalysisValidator {
 private static final Set<String> STATUSES=Set.of("complete","in_progress","remaining","review");
 public ObjectNode validate(JsonNode parsed,Map<Integer,String> pages){
  if(!(parsed instanceof ObjectNode out)||!out.path("requirements").isArray()||!out.path("totals").isObject())throw new IllegalArgumentException("The analysis was incomplete. Please try again.");
  ArrayNode warnings=out.path("warnings") instanceof ArrayNode a?a:out.putArray("warnings");
  for(String field:List.of("major","minor","campus","catalogYear"))if(!out.path(field).isTextual())out.putNull(field);
  if(out.path("major").isNull())warnings.add("The major was not identified. Confirm that this is an academic requirements report.");
  sanitizeCredits((ObjectNode)out.path("totals"),warnings);
  DegreeCreditReconciler.reconcile(out,pages,warnings);
  if(out.path("requirements").size()>150)throw new IllegalArgumentException("Too many requirements were returned. Please use a shorter report.");
  for(var item:out.path("requirements"))if(item instanceof ObjectNode node){node.remove("appliedCourses");node.remove("appliedCoursesVerified");}
  RequirementReconciler.reconcile(out,pages);
  int index=0;
  for(JsonNode value:out.path("requirements")){
   if(!(value instanceof ObjectNode r)||!r.path("title").isTextual()||!r.path("description").isTextual())throw new IllegalArgumentException("The requirement structure was incomplete. Please try again.");
   r.put("id","requirement-"+(++index));sanitizeCredits(r,warnings);
   if(!STATUSES.contains(r.path("status").asText()))r.put("status","review");
   if(!r.path("courses").isArray())r.putArray("courses");
   else for(JsonNode course:r.path("courses"))if(!course.isTextual())throw new IllegalArgumentException("The course list could not be interpreted.");
   ObjectNode evidence=r.path("evidence") instanceof ObjectNode e?e:r.putObject("evidence");
   String quote=evidence.path("quote").asText("");int page=evidence.path("page").asInt(-1);
   boolean verified=!quote.isBlank()&&pages.containsKey(page)&&normalize(pages.get(page)).contains(normalize(quote));evidence.put("verified",verified);
   if(!verified){r.put("status","review");warnings.add("Check the original source for: "+r.path("title").asText());}
  }
  if(out.path("requirements").isEmpty())warnings.add("No degree requirements were identified in this document.");
  warnings.add("Credit totals and requirement completion are separate. Review AI extraction against the original report and confirm decisions with an adviser.");
  return RequirementPlanning.annotate(out);
 }
 private void sanitizeCredits(ObjectNode node,ArrayNode warnings){for(String key:List.of("required","completed","inProgress")){JsonNode n=node.path(key);if(n.isNull()||n.isMissingNode()){node.putNull(key);continue;}if(!n.isNumber()||!Double.isFinite(n.asDouble())||n.asDouble()<0||n.asDouble()>500){node.putNull(key);warnings.add("An invalid credit value was left unknown: "+key);}}}
 private String normalize(String s){return s.replaceAll("\s+"," ").trim();}
}
