package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
final class RequirementPlanning {
 static boolean covered(JsonNode r){
  if("complete".equals(r.path("status").asText()))return true;
  if(!"in_progress".equals(r.path("status").asText()))return false;
  for(String key:new String[]{"required","completed","inProgress"})if(!r.path(key).isNumber()||!Double.isFinite(r.path(key).asDouble())||r.path(key).asDouble()<0)return false;
  return r.path("required").asDouble()>0 && r.path("completed").decimalValue().add(r.path("inProgress").decimalValue()).compareTo(r.path("required").decimalValue())>=0;
 }
 static ObjectNode annotate(ObjectNode report){
  for(var item:report.path("requirements"))if(item instanceof ObjectNode r){boolean covered=covered(r);r.put("coveredByPlan",covered);r.put("planningStatus",covered?("complete".equals(r.path("status").asText())?"completed":"covered_by_planned_courses"):"needs_attention");}
  return report;
 }
}
