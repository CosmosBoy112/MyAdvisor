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

 static ObjectNode evaluate(JsonNode tree,JsonNode reportCourses,Map<String,Set<String>> aliases,ObjectMapper mapper){
  var out=mapper.createObjectNode();
  if(tree==null||tree.isNull()||tree.isMissingNode()){out.put("status","no_prerequisites_in_database");return out;}
  var checked=node(tree,reportCourses,aliases,mapper);out.put("status",checked.path("result").asText());out.set("tree",checked);return out;
 }

 private static ObjectNode node(JsonNode n,JsonNode reportCourses,Map<String,Set<String>> aliases,ObjectMapper mapper){
  var out=(ObjectNode)n.deepCopy();out.remove("children");String type=n.path("type").asText();
  switch(type){
   case "ALL","ANY"->{
    var children=out.putArray("children");var results=new ArrayList<String>();
    for(var c:n.path("children")){var checked=node(c,reportCourses,aliases,mapper);children.add(checked);results.add(checked.path("result").asText());}
    out.put("result",results.isEmpty()?"unknown":type.equals("ALL")?worst(results):best(results));
   }
   case "COURSE","COREQ"->course(out,n,reportCourses,aliases,type.equals("COREQ")||n.path("concurrent_ok").asBoolean(false));
   case "STANDING"->{out.put("result","unknown");out.put("reason","Semester standing is not available from the report.");}
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
