package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;
class MathMinorServiceTest {
 final ObjectMapper mapper=new ObjectMapper();
 ObjectNode report(){var r=mapper.createObjectNode();r.put("catalogYear","2026-27");r.put("campus","University Park");r.putObject("courseInventory").put("method","pdf_course_rows_v1").putArray("courses");return r;}
 void add(ObjectNode r,String code,int credits,String grade){((ArrayNode)r.at("/courseInventory/courses")).addObject().put("code",code).put("term","FA 2026").put("credits",credits).put("grade",grade);}
 @Test void distinguishesEarnedPlannedAndMissingAndAvoidsRepeatCounting()throws Exception{
  var r=report();add(r,"MATH 140",4,"A");add(r,"MATH 141",4,"IP");add(r,"MATH 220",2,"C");add(r,"MATH 230",4,"B");add(r,"MATH 441",3,"B");add(r,"MATH 441",3,"IP");
  var a=new MathMinorService(mapper,Optional.empty()).evaluate(r);assertEquals(13,a.path("completed").asInt());assertEquals(4,a.path("planned").asInt());assertEquals(9,a.path("additionalCredits").asInt());assertEquals("covered_by_plan",a.at("/groups/1/status").asText());assertTrue(a.at("/groups/1/options").isEmpty());assertEquals("needs_review",a.at("/uniqueFromMajor/status").asText());
 }
 @Test void rejectsLowOrUnknownGradesAndCapsRestrictedCourses()throws Exception{
  var r=report();add(r,"MATH 140",4,"C-");add(r,"MATH 141",4,"TR");add(r,"MATH 400",6,"A");add(r,"MATH 414",3,"A");add(r,"MATH 418",3,"B");add(r,"MATH 240",4,"A");
  var a=new MathMinorService(mapper,Optional.empty()).evaluate(r);assertEquals(6,a.at("/groups/3/completed").asInt());assertEquals(0,a.at("/groups/2/completed").asInt());assertEquals(3,a.path("excludedCourses").size());assertFalse(a.path("automaticApproval").asBoolean());
 }
 @Test void missingInventoryIsUnknownNotZeroAndYearMismatchIsVisible()throws Exception{
  var r=mapper.createObjectNode().put("catalogYear","Spring 2023");var a=new MathMinorService(mapper,Optional.empty()).evaluate(r);assertFalse(a.path("inventoryAvailable").asBoolean());assertTrue(a.path("additionalCredits").isNull());assertFalse(a.path("catalogYearMatches").asBoolean());
 }
 @Test void inventoryDeduplicatesSectionsButFlagsConflicts(){
  var r=report();CourseInventory.extract(r,Map.of(1,"SP 2025 MATH 140 Calculus 4.00 A\nSP 2026 MATH 141 Calculus II 4.00 IP\n",2,"SP 2025 MATH 140 Calculus 4.00 A\nSP 2026 MATH 141 Calculus II 3.00 IP\n"));assertEquals(2,r.at("/courseInventory/courses").size());assertTrue(r.at("/courseInventory/courses/1/requiresReview").asBoolean());assertFalse(r.toString().contains("Calculus 4.00"));
 }
 @Test @EnabledIfSystemProperty(named="auditPdf",matches=".+") void realPdfInventoryAndMinorPreview()throws Exception{
  var r=report();r.put("catalogYear","Spring 2023");CourseInventory.extract(r,new PdfReader().read(Files.readAllBytes(Path.of(System.getProperty("auditPdf")))));var a=new MathMinorService(mapper,Optional.empty()).evaluate(r);
  assertTrue(r.at("/courseInventory/courses").size()>30);assertFalse(a.path("catalogYearMatches").asBoolean());System.out.println("MINOR_PREVIEW courses="+r.at("/courseInventory/courseCount")+" earned="+a.path("completed")+" planned="+a.path("planned")+" additional="+a.path("additionalCredits"));
  String out=System.getProperty("minorOutput");if(out!=null){r.set("minorAssessment",a);Files.writeString(Path.of(out),r.toString());}
 }
}
