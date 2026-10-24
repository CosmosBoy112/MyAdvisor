package dev.lionpathway;

import com.fasterxml.jackson.databind.node.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.*;

/** Reconcile a recognized LionPATH degree-total table, never sum overlapping requirements. */
final class DegreeCreditReconciler {
 private static final Pattern SUMMARY=Pattern.compile("Computer Science Major,\\s*\\(CMPSC_BS\\) Total\\s+(\\d+(?:\\.\\d+)?) units are required for the degree\\s*[··•]?\\s*Units:\\s*([\\d.]+) required,\\s*([\\d.]+) used,\\s*([\\d.]+) needed",Pattern.CASE_INSENSITIVE);
 private static final Pattern ROW=Pattern.compile("(?ms)^\\h*(SP|SU|FA)\\h+(20\\d{2})\\h+([A-Z]+)\\h+(\\d+[A-Z]*)\\h+(.+?)(?=^\\h*(?:SP|SU|FA)\\h+20\\d{2}\\h+[A-Z]+\\h+\\d|\\z)");
 private static final Pattern GRADE=Pattern.compile("(?m)(\\d+\\.\\d{2})\\h+(IP|[ABCD][+-]?|TR|CR|PA|SAT)\\h*$");
 static void reconcile(ObjectNode report,Map<Integer,String> pages,ArrayNode warnings){
  for(var page:pages.entrySet()){
   String text=page.getValue();Matcher summary=SUMMARY.matcher(text);if(!summary.find())continue;
   ObjectNode totals=(ObjectNode)report.path("totals");
   // A 'used' summary includes IP and is not evidence of earned credit by itself.
   String tail=text.substring(summary.end());int start=tail.indexOf("Total units");int end=tail.indexOf("Knowledge Domain Flexibility");
   if(start<0||end<=start){unverified(totals,warnings);return;}
   String table=tail.substring(start,end);Matcher rows=ROW.matcher(table);
   BigDecimal all=BigDecimal.ZERO,ip=BigDecimal.ZERO;Set<String> seen=new HashSet<>();boolean valid=true;int count=0;
   ObjectNode terms=totals.objectNode();
   while(rows.find()){
    Matcher grade=GRADE.matcher(rows.group(5));
    if(!grade.find()){valid=false;break;}
    String key=rows.group(1)+rows.group(2)+rows.group(3)+rows.group(4);
    if(!seen.add(key)){valid=false;break;}
    BigDecimal credits=new BigDecimal(grade.group(1));all=all.add(credits);count++;
    if(grade.group(2).equals("IP")){
     ip=ip.add(credits);String term=rows.group(1)+" "+rows.group(2);
     terms.put(term,terms.path(term).decimalValue().add(credits));
    }
   }
   BigDecimal required=new BigDecimal(summary.group(2)),used=new BigDecimal(summary.group(3)),needed=new BigDecimal(summary.group(4));
   if(!valid||count==0||all.compareTo(used)!=0||required.subtract(used).compareTo(needed)!=0||required.compareTo(new BigDecimal(summary.group(1)))!=0){unverified(totals,warnings);return;}
   totals.put("required",required);totals.put("completed",used.subtract(ip));totals.put("inProgress",ip);
   ObjectNode evidence=totals.putObject("evidence");evidence.put("page",page.getKey());evidence.put("quote",summary.group());
   evidence.put("method","degree_table_reconciled");evidence.put("verified",true);evidence.put("usedIncludingInProgress",used);evidence.put("neededAfterInProgress",needed);evidence.put("courseCount",count);
   totals.set("inProgressByTerm",terms);totals.put("includesPlannedTerms",true);
   warnings.add("Degree totals were reconciled against the report's degree-total course table. 'Used' includes IP credits; IP can include future registered terms. These figures describe the report date, not today's enrollment.");return;
  }
 }
 private static void unverified(ObjectNode totals,ArrayNode warnings){totals.putNull("completed");totals.putNull("inProgress");warnings.add("The degree summary uses 'used' credits, which may include IP. Its course table could not be fully reconciled; earned and in-progress totals remain unknown.");}
}
