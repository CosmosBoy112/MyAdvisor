package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.*;
/**
 * Builds the screen the agent asked for (show_view). The model only picks the screen and its courses or nodes;
 * every number and status here comes from the report or the catalog tools, never from model text.
 */
final class AdvisorViews {
 // "CMPSC 121 or 131 (GQ)", "CAS 100A or 100B": a bare number reuses the last subject.
 private static final Pattern CODE=Pattern.compile("\\b(?:([A-Z]{2,6})\\s+)?(\\d{1,3}[A-Z]{0,2})\\b");
 private AdvisorViews(){}

 static ObjectNode build(AdvisorTools tools,AdvisorTools.Turn turn,ObjectMapper mapper){
  var request=turn.viewRequest;if(request==null)return null;
  String type=request.path("view").asText();var view=mapper.createObjectNode();view.put("type",type);var warnings=view.putArray("warnings");
  var data=switch(type){
   case "course_detail"->courseDetail(tools,turn,request,mapper,warnings);
   case "course_options"->courseOptions(tools,turn,request,mapper,warnings);
   case "requirement_gap"->requirementGap(tools,turn,request,mapper,warnings);
   case "semester_plan"->semesterPlan(tools,turn,mapper,warnings);
   default->minorCheck(tools,turn,mapper,warnings);
  };
  view.set("data",data);return view;
 }

 private static ObjectNode courseDetail(AdvisorTools tools,AdvisorTools.Turn turn,JsonNode request,ObjectMapper mapper,ArrayNode warnings){
  var data=mapper.createObjectNode();var courses=data.putArray("courses");
  for(var c:request.path("course_codes")){
   var args=mapper.createObjectNode().put("code",c.asText());var item=courses.addObject();item.put("code",c.asText());
   var course=tools.call("get_course",args,turn);
   if(!course.path("ok").asBoolean()){item.put("status","not_in_catalog_database");item.put("message",course.at("/error/message").asText());continue;}
   item.set("course",course.path("data"));
   var prereq=tools.call("check_prerequisites",args,turn);
   if(prereq.path("ok").asBoolean())item.set("prerequisites",prereq.path("data"));else item.put("prerequisitesMessage",prereq.at("/error/message").asText());
   addWarnings(warnings,course);addWarnings(warnings,prereq);
  }
  return data;
 }

 private static ObjectNode courseOptions(AdvisorTools tools,AdvisorTools.Turn turn,JsonNode request,ObjectMapper mapper,ArrayNode warnings){
  var data=mapper.createObjectNode();var courses=data.putArray("courses");
  for(var c:request.path("course_codes")){
   var args=mapper.createObjectNode().put("code",c.asText());var item=courses.addObject();item.put("code",c.asText());
   var course=tools.call("get_course",args,turn);
   if(!course.path("ok").asBoolean()){item.put("status","not_in_catalog_database");continue;}
   for(String f:List.of("title","credits","attributes","source_url"))if(course.path("data").has(f))item.set(f,course.path("data").get(f));
   var prereq=tools.call("check_prerequisites",args,turn);
   item.put("prerequisiteStatus",prereq.path("ok").asBoolean()?prereq.at("/data/status").asText():"unknown");
   item.put("inReport",inReport(turn,c.asText()));
   addWarnings(warnings,prereq);
  }
  return data;
 }

 private static ObjectNode requirementGap(AdvisorTools tools,AdvisorTools.Turn turn,JsonNode request,ObjectMapper mapper,ArrayNode warnings){
  var data=mapper.createObjectNode();var open=data.putArray("requirements");
  for(var r:turn.report.path("requirements")){
   if(r.path("coveredByPlan").asBoolean(false)||r.path("status").asText().equals("completed"))continue;
   var item=open.addObject();for(String f:List.of("title","status","required","completed","inProgress","description","evidence"))if(r.has(f))item.set(f,r.get(f));
  }
  var options=data.putArray("courseOptions");
  for(var n:request.path("node_ids")){
   var found=tools.call("find_courses_for_requirement",mapper.createObjectNode().put("node_id",n.asLong()),turn);
   if(found.path("ok").asBoolean()){options.add(found.path("data"));addWarnings(warnings,found);}
  }
  return data;
 }

 private static ObjectNode semesterPlan(AdvisorTools tools,AdvisorTools.Turn turn,ObjectMapper mapper,ArrayNode warnings){
  var data=mapper.createObjectNode();
  String option=turn.scope.map(CatalogDbService.Scope::option).orElse(null);
  // Programs without options (CMPEN, EE) store their one plan under DEFAULT.
  if(option==null&&turn.scope.isPresent()&&turn.scope.get().program().options().isEmpty())option="DEFAULT";
  if(turn.scope.isEmpty()||option==null){data.put("status","unavailable");data.put("message","A suggested plan needs the report's major and option to match a loaded program.");return data;}
  var policy=tools.call("get_policies",mapper.createObjectNode().put("key","suggested_academic_plan."+option),turn);
  if(!policy.path("ok").asBoolean()){data.put("status","unavailable");data.put("message","No suggested semester plan is loaded for "+turn.scope.get().program().code()+" "+option+".");return data;}
  var rule=policy.at("/data/policies/0");var plan=rule.path("value");
  data.put("status","reference_plan");data.put("program",turn.scope.get().program().name());if(!option.equals("DEFAULT"))data.put("option",option);data.set("totalCredits",plan.path("total_credits"));
  if(rule.has("source_url"))data.set("sourceUrl",rule.get("source_url"));
  var terms=data.putArray("terms");
  for(var t:plan.path("terms")){
   var term=terms.addObject();term.set("year",t.path("year"));term.set("season",t.path("season"));term.set("totalCredits",t.path("total_credits"));var courses=term.putArray("courses");
   for(var c:t.path("courses")){
    var item=(ObjectNode)c.deepCopy();var codes=codes(c.path("label").asText());item.set("codes",mapper.valueToTree(codes));
    String status="generic";for(String code:codes){String s=inReport(turn,code);if(s.startsWith("recorded")){status=s;break;}if(s.equals("in_progress"))status=s;else if(status.equals("generic"))status="not_in_report";}
    item.put("inReport",status);courses.add(item);
   }
  }
  addWarnings(warnings,policy);
  warnings.add("This is the Bulletin's suggested plan with your report's courses marked, not a personalized or validated schedule.");
  return data;
 }

 private static ObjectNode minorCheck(AdvisorTools tools,AdvisorTools.Turn turn,ObjectMapper mapper,ArrayNode warnings){
  var result=tools.call("get_minor_assessment",mapper.createObjectNode().put("minor","MATH"),turn);
  if(!result.path("ok").asBoolean()){var data=mapper.createObjectNode();data.put("status","unavailable");data.put("message",result.at("/error/message").asText());return data;}
  addWarnings(warnings,result);return (ObjectNode)result.path("data");
 }

 static List<String> codes(String label){
  var out=new ArrayList<String>();var m=CODE.matcher(label);String subject=null;
  while(m.find()){if(m.group(1)!=null)subject=m.group(1);if(subject!=null)out.add(subject+" "+m.group(2));}
  return out;
 }
 /** no / in_progress / recorded:GRADE for the best row of this course in the report. */
 static String inReport(AdvisorTools.Turn turn,String code){
  String best="no";
  for(var c:turn.report.path("courseInventory").path("courses")){
   if(!c.path("code").asText().equals(code))continue;String grade=c.path("grade").asText();
   if(grade.equals("IP")){if(best.equals("no"))best="in_progress";}else best="recorded:"+grade;
  }
  return best;
 }
 private static void addWarnings(ArrayNode warnings,JsonNode result){for(var w:result.path("warnings"))if(!warnings.toString().contains(w.toString()))warnings.add(w);}
}
