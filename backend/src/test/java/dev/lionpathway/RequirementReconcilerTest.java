package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RequirementReconcilerTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private String page(){return """
 Writing
 Not Satisfied: Complete 3 units
 · Units: 3.00 required, 0.00 used, 3.00 needed
 Programming
 Satisfied
 · Units: 3.00 required, 3.00 used
 Term Subject Cat # Course Title Units Grade
 SP 2025 CMPSC 131 Programming 3.00 A
 Speech
 Satisfied
 · Units: 3.00 required, 3.00 used
 Term Subject Cat # Course Title Units Grade
 SP 2026 CAS 100A Speech 3.00 IP
 """;}
 private ObjectNode report(){var r=mapper.createObjectNode();r.putArray("requirements");r.putObject("totals");return r;}
 @Test void restoresOmittedCompletedSectionsButDoesNotCountIpAsEarned(){
  var r=report();assertEquals(2,RequirementReconciler.reconcile(r,Map.of(1,page())));
  assertEquals("complete",r.at("/requirements/0/status").asText());assertEquals(3,r.at("/requirements/0/completed").asInt());
  assertEquals("in_progress",r.at("/requirements/1/status").asText());assertEquals(0,r.at("/requirements/1/completed").asInt());assertEquals(3,r.at("/requirements/1/inProgress").asInt());
  assertEquals("CMPSC 131",r.at("/requirements/0/appliedCourses/0/code").asText());assertEquals("Programming",r.at("/requirements/0/appliedCourses/0/title").asText());assertEquals("SP 2025",r.at("/requirements/0/appliedCourses/0/term").asText());assertEquals("A",r.at("/requirements/0/appliedCourses/0/grade").asText());assertEquals("in_progress",r.at("/requirements/1/appliedCourses/0/status").asText());
  assertEquals(0,RequirementReconciler.reconcile(r,Map.of(1,page())));assertEquals(2,r.path("requirements").size());
 }
 @Test void sameTitleOnDifferentPagesKeepsDistinctRequirementCredits(){
  var r=report();RequirementReconciler.reconcile(r,Map.of(1,"Science\nSatisfied\nUnits: 1.00 required, 3.00 used\nFA 2025 EGEE 102 Energy 3.00 A\n",2,"Science\nSatisfied\nUnits: 2.00 required, 3.00 used\nFA 2025 EGEE 102 Energy 3.00 A\n"));
  assertEquals(2,r.path("requirements").size());assertEquals(1,r.at("/requirements/0/required").asInt());assertEquals(2,r.at("/requirements/1/required").asInt());
 }
 @Test void incompleteOrDuplicatedTablesNeedReview(){
  for(String text:List.of(page().replace("Programming 3.00 A","Programming 2.00 A"),page().replace("Programming 3.00 A","Programming 3.00 A\nSP 2025 CMPSC 131 Programming 3.00 A"))){
   var r=report();RequirementReconciler.reconcile(r,Map.of(1,text));assertEquals("review",r.at("/requirements/0/status").asText());assertTrue(r.at("/requirements/0/completed").isNull());assertTrue(r.at("/requirements/0/appliedCourses").isEmpty());assertFalse(r.at("/requirements/0/appliedCoursesVerified").asBoolean());
  }
 }
 @Test void nonAuditTextAndNotSatisfiedNeverBecomeComplete(){
  var r=report();assertEquals(0,RequirementReconciler.reconcile(r,Map.of(1,"Review\nSatisfied\nJust a review",2,"Writing\nNot Satisfied: Complete 3 units\nUnits: 3.00 required, 0.00 used")));
 }
 @Test void sectionCanContinueOnNextPage(){
  var r=report();RequirementReconciler.reconcile(r,Map.of(1,"Programming\nSatisfied\n",2,"Page header\nUnits: 3.00 required, 3.00 used\nSP 2025 CMPSC 131 Programming 3.00 A\n"));assertEquals("complete",r.at("/requirements/0/status").asText());assertEquals(1,r.at("/requirements/0/evidence/page").asInt());assertEquals(2,r.at("/requirements/0/appliedCourses/0/page").asInt());
 }
 @Test @EnabledIfSystemProperty(named="auditPdf",matches=".+") void realPdfCoverage()throws Exception{
  var pages=new PdfReader().read(Files.readAllBytes(Path.of(System.getProperty("auditPdf"))));var r=report();RequirementReconciler.reconcile(r,pages);
  long completed=0,ip=0,review=0;for(var n:r.path("requirements")){switch(n.path("status").asText()){case "complete"->completed++;case "in_progress"->ip++;default->review++;}}
  assertTrue(completed>0);assertTrue(ip>0);System.out.println("PDF restored section counts: complete="+completed+", in_progress="+ip+", review="+review);
  String output=System.getProperty("repairedSectionsOutput");if(output!=null)Files.writeString(Path.of(output),r.path("requirements").toString());
 }
}
