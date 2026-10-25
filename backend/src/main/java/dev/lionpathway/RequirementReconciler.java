package dev.lionpathway;

import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Recover explicit satisfied LionPATH sections omitted by model extraction, without inferring from credit gaps. */
final class RequirementReconciler {
 private static final Pattern SECTION=Pattern.compile("(?m)^([^\\r\\n]+)\\r?\\n(Satisfied|Not Satisfied:[^\\r\\n]*)\\h*$");
 private static final Pattern UNITS=Pattern.compile("Units:\\s*([\\d.]+) required,\\s*([\\d.]+) used");
 private static final Pattern ROW=Pattern.compile("(?ms)^\\h*(SP|SU|FA|WI)\\h+(20\\d{2})\\h+([A-Z]+)\\h+(\\d+[A-Z]*)\\h+(.+?)(?=^\\h*(?:SP|SU|FA|WI)\\h+20\\d{2}\\h+[A-Z]+\\h+\\d|\\z)");
 private static final Pattern GRADE=Pattern.compile("(?m)(\\d+\\.\\d{2})\\h+(IP|[ABCD][+-]?|TR|CR|PA|SAT)\\h*$");
 private record Section(String title,String state,int start,int end,int page,String quote){}
 static int reconcile(ObjectNode report,Map<Integer,String> pages){
  if(!(report.path("requirements") instanceof ArrayNode requirements))return 0;
  var text=new StringBuilder();var sections=new ArrayList<Section>();
  for(var entry:new TreeMap<>(pages).entrySet()){
   int offset=text.length();String page=entry.getValue();var matcher=SECTION.matcher(page);
   while(matcher.find())sections.add(new Section(matcher.group(1).strip(),matcher.group(2),offset+matcher.start(),offset+matcher.end(),entry.getKey(),matcher.group()));
   text.append(page).append('\n');
  }
  int restored=0;
  for(int i=0;i<sections.size();i++){
   var section=sections.get(i);if(!section.state.equals("Satisfied"))continue;
   String block=text.substring(section.end,i+1<sections.size()?sections.get(i+1).start:text.length());
   var units=UNITS.matcher(block);boolean hasUnits=units.find()&&units.start()<500;
   // Only recognized credit/GPA summaries; do not treat arbitrary prose saying Satisfied as an audit section.
   boolean gpa=!hasUnits&&Pattern.compile("GPA:\\s*[\\d.]+ required,\\s*[\\d.]+ completed").matcher(block.substring(0,Math.min(block.length(),300))).find();
   if(!hasUnits&&!gpa)continue;
   BigDecimal required=hasUnits?new BigDecimal(units.group(1)):null,used=hasUnits?new BigDecimal(units.group(2)):null;
   ObjectNode target=null;
   for(var item:requirements)if(item instanceof ObjectNode node && normalized(node.path("title").asText()).equals(normalized(section.title)) && node.path("evidence").path("page").asInt(-1)==section.page && (!hasUnits || node.path("required").isNumber()&&node.path("required").decimalValue().compareTo(required)==0)){target=node;break;}
   // Repeat calls cannot add duplicates even when the original item lacked numeric totals.
   if(target==null)for(var item:requirements)if(item instanceof ObjectNode node&&node.path("evidence").path("page").asInt(-1)==section.page&&node.path("evidence").path("quote").asText().equals(section.quote)){target=node;break;}
   if(target==null){if(requirements.size()>=150)break;target=requirements.addObject();target.put("title",section.title);target.putArray("courses");restored++;}
   target.put("reportedStatus","satisfied");target.put("description","The PDF marks this section satisfied. In-progress courses are shown separately from earned credits.");
   target.putNull("required");target.putNull("completed");target.putNull("inProgress");
   String state=gpa?"complete":"review";
   if(hasUnits){
    target.put("required",required);var rows=ROW.matcher(block.substring(units.end()));
    BigDecimal sum=BigDecimal.ZERO,ip=BigDecimal.ZERO;var seen=new HashSet<String>();boolean valid=true;int count=0;
    while(rows.find()){
     var grade=GRADE.matcher(rows.group(5));String key=rows.group(1)+rows.group(2)+rows.group(3)+rows.group(4);
     if(!grade.find()||!seen.add(key)){valid=false;break;}
     BigDecimal credit=new BigDecimal(grade.group(1));sum=sum.add(credit);count++;
     if("IP".equals(grade.group(2)))ip=ip.add(credit);
    }
    if(valid&&count>0&&sum.compareTo(used)==0&&used.compareTo(required)>=0){target.put("completed",used.subtract(ip));target.put("inProgress",ip);state=ip.signum()>0?"in_progress":"complete";}
   }
   target.put("status",state);var evidence=target.putObject("evidence");evidence.put("page",section.page);evidence.put("quote",section.quote);evidence.put("verified",true);evidence.put("method","lionpath_satisfied_section");
  }
  for(int i=0;i<requirements.size();i++)((ObjectNode)requirements.get(i)).put("id","requirement-"+(i+1));
  report.put("requirementsRestoredFromPdf",restored);return restored;
 }
 private static String normalized(String s){return s.toLowerCase(Locale.ROOT).replaceAll("\\s+"," ").trim();}
}
