package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
/**
 * Evaluates a prerequisite tree (CatalogDbService.prereqTree) against the course rows found in the report.
 * Results: met, in_progress (met once in-progress courses are passed), not_met, unknown. Never guesses: unparsed text,
 * semester standing and grade equivalences the report cannot prove come back unknown.
 */
final class PrerequisiteChecker {
 // Penn State letter grades, best first. D passes unless the node names a minimum grade.
 private static final List<String> LETTERS=List.of("A","A-","B+","B","B-","C+","C","D");
 private static final Set<String> NOT_EARNED=Set.of("F","W","UN","AU","LD","NG","XF");
 private static final Set<String> PASS_NO_LETTER=Set.of("TR","CR","PA","SA","SAT");
 private static final List<String> RANK=List.of("met","in_progress","unknown","not_met");
 private PrerequisiteChecker(){}

 /**
  * Semester classification estimated from the report's credit totals, per the Bulletin table "Classification of Students
  * by Semester" (total credits earned: 14.0 or fewer = 1, 14.1-29 = 2, ... 134.1-149 = 10, 149.1 or more = 11).
  * earned uses completed credits; withInProgress adds credits in progress. Null fields mean the report has no total.
  */
 record Standing(Integer earned,Integer withInProgress,Double earnedCredits,Double inProgressCredits){
  static final Standing UNKNOWN=new Standing(null,null,null,null);
  static Standing of(JsonNode report){
   var t=report.path("totals");if(!t.path("completed").isNumber())return UNKNOWN;
   double done=t.path("completed").asDouble();Double ip=t.path("inProgress").isNumber()?t.path("inProgress").asDouble():null;
   return new Standing(classification(done),ip==null?null:classification(done+ip),done,ip);
  }
 }
 private static final double[] UPPER={14,29,44,59,74,89,104,119,134,149};
 static int classification(double credits){int c=1;for(double u:UPPER)if(credits>u)c++;return c;}
 static final String STANDING_SOURCE="Semester standing is estimated from the report's completed credits using the Bulletin table \"Classification of Students by Semester\"; LionPATH uses total credits earned, including transfer and AP credit.";

 static ObjectNode evaluate(JsonNode tree,JsonNode reportCourses,Map<String,Set<String>> aliases,ObjectMapper mapper){return evaluate(tree,reportCourses,aliases,Standing.UNKNOWN,mapper);}
 static ObjectNode evaluate(JsonNode tree,JsonNode reportCourses,Map<String,Set<String>> aliases,Standing standing,ObjectMapper mapper){
  var out=mapper.createObjectNode();
  if(tree==null||tree.isNull()||tree.isMissingNode()){out.put("status","no_prerequisites_in_database");return out;}
  var checked=node(tree,reportCourses,aliases,standing,mapper);out.put("status",checked.path("result").asText());out.set("tree",checked);return out;
 }
 /** met / in_progress / not_met / unknown for a minimum semester classification, with the reason. */
 static String[] standing(int min,Standing s){
  if(s.earned()==null)return new String[]{"unknown","The report has no completed-credit total, so semester standing cannot be estimated."};
  String basis=" (estimated semester "+s.earned()+" from "+fmt(s.earnedCredits())+" completed credits)";
  if(s.earned()>=min)return new String[]{"met","Requires semester "+min+" standing; met"+basis+"."};
  if(s.withInProgress()!=null&&s.withInProgress()>=min)return new String[]{"in_progress","Requires semester "+min+" standing; reached once current courses are passed (semester "+s.withInProgress()+" with "+fmt(s.inProgressCredits())+" credits in progress)"+basis+"."};
  return new String[]{"not_met","Requires semester "+min+" standing; not yet"+basis+"."};
 }
 private static String fmt(Double d){return d==null?"?":d%1==0?String.valueOf(d.intValue()):String.valueOf(d);}
 /** ALL semantics over results: any not_met fails, then unknown, then in_progress. */
 static String combine(List<String> results){return results.isEmpty()?"unknown":worst(results);}

 private static ObjectNode node(JsonNode n,JsonNode reportCourses,Map<String,Set<String>> aliases,Standing standing,ObjectMapper mapper){
  var out=(ObjectNode)n.deepCopy();out.remove("children");String type=n.path("type").asText();
  switch(type){
   case "ALL","ANY"->{
    var children=out.putArray("children");var results=new ArrayList<String>();
    for(var c:n.path("children")){var checked=node(c,reportCourses,aliases,standing,mapper);children.add(checked);results.add(checked.path("result").asText());}
    out.put("result",results.isEmpty()?"unknown":type.equals("ALL")?worst(results):best(results));
   }
   case "COURSE","COREQ"->course(out,n,reportCourses,aliases,type.equals("COREQ")||n.path("concurrent_ok").asBoolean(false));
   case "STANDING"->{
    if(n.path("min_semester").isInt()){var r=standing(n.path("min_semester").asInt(),standing);out.put("result",r[0]);out.put("reason",r[1]);if(standing.earned()!=null)out.put("estimate",true);}
    else{out.put("result","unknown");out.put("reason","The required semester standing could not be read.");}
   }
   default->{out.put("result","unknown");out.put("reason","The Bulletin wording could not be parsed; quote it instead of interpreting it.");}
  }
  return out;
 }

 private static void course(ObjectNode out,JsonNode n,JsonNode reportCourses,Map<String,Set<String>> aliases,boolean concurrent){
  String code=n.path("course").asText();String minGrade=n.path("min_grade").asText(null);
  var codes=new LinkedHashSet<String>();codes.add(code);codes.addAll(aliases.getOrDefault(code,Set.of()));
  String result="not_met";JsonNode used=null;String reason="No matching course row was found in the report.";
  for(var c:reportCourses){
   if(!codes.contains(c.path("code").asText()))continue;
   String grade=c.path("grade").asText("").toUpperCase(Locale.ROOT);String r;String why;
   if(c.path("requiresReview").asBoolean(false)){r="unknown";why="The report has conflicting rows for this course.";}
   else if(grade.equals("IP")){r=concurrent?"met":"in_progress";why=concurrent?"In progress; this requirement allows concurrent enrollment.":"In progress; met only once it is passed"+(minGrade==null?".":" with "+minGrade+" or better.");}
   else if(NOT_EARNED.contains(grade)){r="not_met";why="Recorded grade "+grade+" does not earn the course.";}
   else if(PASS_NO_LETTER.contains(grade)){r=minGrade==null?"met":"unknown";why=minGrade==null?"Earned as "+grade+".":"Earned as "+grade+"; whether it meets the "+minGrade+" minimum is not verified.";}
   else if(LETTERS.contains(grade.replace("A+","A"))){
    int have=LETTERS.indexOf(grade.replace("A+","A"));
    if(minGrade==null){r="met";why="Completed with "+grade+".";}
    else if(LETTERS.contains(minGrade)){r=have<=LETTERS.indexOf(minGrade)?"met":"not_met";why="Completed with "+grade+"; requires "+minGrade+" or better.";}
    else{r="unknown";why="Completed with "+grade+"; minimum grade "+minGrade+" is not a letter grade.";}
   }
   else{r="unknown";why="Grade "+(grade.isEmpty()?"missing":grade)+" could not be interpreted.";}
   if(RANK.indexOf(r)<RANK.indexOf(result)||used==null&&r.equals(result)){result=r;used=c;reason=why;}
  }
  out.put("result",result);out.put("reason",reason);
  if(used!=null){var u=out.putObject("matched_record");for(String f:List.of("code","term","grade","credits","page"))if(used.has(f))u.set(f,used.get(f));}
  if(concurrent){
   // Concurrent enrollment is an allowance, not a pairing rule.
   out.put("may_take_concurrently",true);
   out.put("meaning","Take "+code+" before this course or in the same term; it does not have to be taken together.");
  }
 }
 private static String best(List<String> results){return results.stream().min(Comparator.comparingInt(RANK::indexOf)).orElse("unknown");}
 // ALL: any not_met fails; otherwise unknown outranks in_progress, which outranks met.
 private static String worst(List<String> results){
  if(results.contains("not_met"))return "not_met";if(results.contains("unknown"))return "unknown";if(results.contains("in_progress"))return "in_progress";return "met";
 }
}
