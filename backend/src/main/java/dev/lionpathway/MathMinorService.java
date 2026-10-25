package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.*;
import org.springframework.stereotype.Service;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
public class MathMinorService {
 private final ObjectMapper mapper;private final JsonNode seed;private final Map<String,JsonNode> courseFacts=new LinkedHashMap<>();private final Optional<JdbcTemplate> jdbc;
 public MathMinorService(ObjectMapper mapper,Optional<JdbcTemplate> jdbc)throws Exception{
  this.mapper=mapper;this.jdbc=jdbc;
  try(var in=new ClassPathResource("catalog/2026-27/programs/MATH_UMNR.json").getInputStream()){seed=mapper.readTree(in);}
  try(var in=new ClassPathResource("catalog/2026-27/courses.json").getInputStream()){for(var c:mapper.readTree(in).path("courses"))courseFacts.put(c.path("code").asText(),c);}
 }
 public ObjectNode evaluate(JsonNode report){
  JsonNode rules=seed;String storage="bundled_catalog";
  if(jdbc.isPresent())try{var rows=jdbc.get().queryForList("SELECT value::text FROM policy_rules WHERE key='minor_audit_rules_v1' AND catalog_year='2026-27' AND scope='program' AND scope_code='MATH_UMNR'",String.class);if(rows.size()==1){var db=mapper.readTree(rows.getFirst());if(db.equals(seed)){rules=db;storage="database";}else storage="bundled_catalog_database_differs";}}catch(Exception e){storage="bundled_catalog_database_unavailable";}
  return evaluate(report,rules,storage);
 }
 ObjectNode evaluate(JsonNode report,JsonNode rules,String storage){
  var result=mapper.createObjectNode();result.put("programCode","MATH_UMNR");result.put("name",rules.at("/program/name").asText());result.put("catalogYear","2026-27");result.put("sourceUrl",rules.path("source_url").asText());result.put("departmentUrl",rules.path("department_url").asText());result.put("storage",storage);result.put("status","reference_requires_review");result.put("automaticApproval",false);
  String year=report.path("catalogYear").asText("");result.put("reportCatalogYear",year);result.put("catalogYearMatches",Pattern.compile("2026\\s*[-–/]\\s*(2027|27)").matcher(year).find());
  result.put("campusMatches",Set.of("university park","up").contains(report.path("campus").asText().toLowerCase(Locale.ROOT).strip()));
  var inventory=report.path("courseInventory");boolean available=inventory.path("method").asText().equals("pdf_course_rows_v1")&&inventory.path("courses").isArray();result.put("inventoryAvailable",available);result.put("inventoryCoverage","report_only");result.put("unparsedRowCount",inventory.path("unparsedRowCount").asInt());
  var review=result.putArray("reviewNotes");for(var note:rules.path("review_notes"))review.add(note.asText());
  var excluded=result.putArray("excludedCourses");var best=new LinkedHashMap<String,JsonNode>();
  if(available)for(var c:inventory.path("courses")){
   String code=c.path("code").asText(),grade=c.path("grade").asText();if(!code.startsWith("MATH "))continue;
   boolean passing=Set.of("A","A-","A+","B+","B","B-","C+","C").contains(grade),planned=grade.equals("IP");
   if(c.path("requiresReview").asBoolean()||!c.path("credits").isNumber()||c.path("credits").asDouble()<=0||(!passing&&!planned)){
    var x=excluded.addObject();x.setAll((ObjectNode)c.deepCopy());x.put("reason",c.path("requiresReview").asBoolean()?"conflicting_records":Set.of("C-","D","D-","D+","F","W","UN","AU").contains(grade)?"minimum_grade_not_met":"grade_equivalence_unverified");continue;
   }
   var old=best.get(code);if(old==null || (old.path("grade").asText().equals("IP")&&!planned) || old.path("grade").asText().equals(grade)&&c.path("credits").asDouble()>old.path("credits").asDouble())best.put(code,c);
  }
  for(var pair:rules.path("conflicting_pairs")){
   String a=pair.path(0).asText(),b=pair.path(1).asText();if(best.containsKey(a)&&best.containsKey(b)){
    var left=best.get(a);var right=best.get(b);String remove=left.path("grade").asText().equals("IP")&&!right.path("grade").asText().equals("IP")?a:!left.path("grade").asText().equals("IP")&&right.path("grade").asText().equals("IP")?b:left.path("credits").asDouble()>=right.path("credits").asDouble()?b:a;
    var omitted=excluded.addObject();omitted.setAll((ObjectNode)best.remove(remove).deepCopy());omitted.put("reason","overlapping_course_pair");
   }
  }
  var groups=result.putArray("groups");double totalEarned=0,totalPlanned=0,totalRemaining=0;
  for(var group:rules.path("groups")){
   var out=groups.addObject();String id=group.path("id").asText();out.put("id",id);out.put("label",group.path("label").asText());double required=group.path("min_credits").asDouble();out.put("required",required);
   var matched=out.putArray("matchedCourses");double earned=0,planned=0;
   for(var c:best.values())if(matches(group,c.path("code").asText())){
    var match=(ObjectNode)c.deepCopy();double credits=c.path("credits").asDouble();var cap=group.path("course_caps").path(c.path("code").asText());if(cap.isNumber())credits=Math.min(credits,cap.asDouble());match.put("countedCredits",credits);matched.add(match);if(c.path("grade").asText().equals("IP"))planned+=credits;else earned+=credits;
   }
   if(!available){out.putNull("completed");out.putNull("inProgress");out.putNull("remaining");out.put("status","unknown");}
   else{out.put("completed",earned);out.put("inProgress",planned);double remaining=Math.max(0,required-earned-planned);out.put("remaining",remaining);out.put("status",earned>=required?"completed":remaining==0?"covered_by_plan":"additional_needed");totalEarned+=Math.min(required,earned);totalPlanned+=Math.min(Math.max(0,required-earned),planned);totalRemaining+=remaining;}
   var options=out.putArray("options");
   if(available&&out.path("remaining").asDouble()>0)for(var fact:courseFacts.values()){
    String code=fact.path("code").asText();if(!matches(group,code)||best.containsKey(code)||conflictsWithTaken(rules,best,code))continue;
    var option=options.addObject();option.put("code",code);option.set("title",fact.path("title"));option.set("creditsMin",fact.path("credits_min"));option.set("creditsMax",fact.path("credits_max"));option.set("prerequisiteText",fact.path("prereq_text"));option.set("sourceUrl",fact.path("source_url"));option.put("eligibility","not_verified");
   }
  }
  result.put("minimumCredits",26);if(available){result.put("completed",totalEarned);result.put("planned",totalPlanned);result.put("additionalCredits",totalRemaining);}else{result.putNull("completed");result.putNull("planned");result.putNull("additionalCredits");}
  result.putObject("uniqueFromMajor").put("required",6).put("status","needs_review");
  result.put("declarationStatus","not_verified");return result;
 }
 private boolean conflictsWithTaken(JsonNode rules,Map<String,JsonNode> taken,String code){for(var pair:rules.path("conflicting_pairs"))if(pair.path(0).asText().equals(code)&&taken.containsKey(pair.path(1).asText())||pair.path(1).asText().equals(code)&&taken.containsKey(pair.path(0).asText()))return true;return false;}
 private boolean matches(JsonNode group,String code){if(group.has("courses")){for(var c:group.path("courses"))if(c.asText().equals(code))return true;return false;}return code.matches("MATH 4\\d{2}[A-Z]*");}
}
