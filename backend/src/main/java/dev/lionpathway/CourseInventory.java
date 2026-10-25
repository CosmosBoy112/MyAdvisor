package dev.lionpathway;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.*;
/** Extract every recognizable course-table row, deduplicating repeated requirement appearances. */
final class CourseInventory {
 private static final Pattern ROW=Pattern.compile("(?ms)^\\h*(SP|SU|FA|WI)\\h+(20\\d{2})\\h+([A-Z]{2,8})\\h+(\\d{1,4}[A-Z]*)\\h+(.+?)(?=^\\h*(?:SP|SU|FA|WI)\\h+20\\d{2}\\h+[A-Z]+\\h+\\d|\\z)");
 private static final Pattern GRADE=Pattern.compile("(?m)(\\d+\\.\\d{2})\\h+(IP|[ABCDF][+-]?|TR|CR|PA|SAT|SA|UN|W|AU)\\h*$");
 static ObjectNode extract(ObjectNode report,Map<Integer,String> pages){
  var inventory=report.putObject("courseInventory");inventory.put("coverage","report_only");inventory.put("method","pdf_course_rows_v1");
  var courses=inventory.putArray("courses");var unmatched=inventory.putArray("unparsedRows");var byKey=new LinkedHashMap<String,ObjectNode>();
  for(var page:new TreeMap<>(pages).entrySet()){
   var rows=ROW.matcher(page.getValue());
   while(rows.find()){
    String code=rows.group(3)+" "+rows.group(4),term=rows.group(1)+" "+rows.group(2),key=code+"|"+term;var grade=GRADE.matcher(rows.group(5));
    if(!grade.find()){unmatched.addObject().put("code",code).put("term",term).put("page",page.getKey());continue;}
    double credits=Double.parseDouble(grade.group(1));if(credits<0||credits>30)continue;
    var existing=byKey.get(key);
    if(existing!=null){if(existing.path("credits").asDouble()!=credits||!existing.path("grade").asText().equals(grade.group(2)))existing.put("requiresReview",true);continue;}
    ObjectNode c=courses.addObject();byKey.put(key,c);c.put("code",code);c.put("term",term);c.put("credits",credits);c.put("grade",grade.group(2));c.put("page",page.getKey());c.put("status","IP".equals(grade.group(2))?"in_progress":"recorded");c.put("requiresReview",false);
    String title=rows.group(5).substring(0,grade.start()).replaceAll("\\s+"," ").strip();if(title.length()<=160&&!title.contains(":")&&!title.contains("Report Generated"))c.put("title",title);else c.putNull("title");
   }
  }
  inventory.put("unparsedRowCount",unmatched.size());inventory.put("courseCount",courses.size());return report;
 }
}
