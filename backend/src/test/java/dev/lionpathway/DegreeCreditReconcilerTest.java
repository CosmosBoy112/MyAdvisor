package dev.lionpathway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class DegreeCreditReconcilerTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private String page(){return """
 Computer Science Major, (CMPSC_BS) Total
 12 units are required for the degree
 · Units: 12.00 required, 9.00 used, 3.00 needed
 Computer Science Major, (CMPSC_BS) Total units
 Term Subject Cat # Course Title Units Grade
 SP 2025 CMPSC 131 Programming 3.00 A
 SP 2026 CMPSC 132 Programming
 II
 3.00 IP
 FA 2026 CMPSC 221 Programming 3.00 IP
 Knowledge Domain Flexibility
 SP 2025 ASTRO 1 Excluded 3.00 A
 """;}
 @Test void usedIncludesFutureIpAndNeededIsNotIp(){
  var r=mapper.createObjectNode();r.putObject("totals").put("completed",9).put("inProgress",3);var w=r.putArray("warnings");
  DegreeCreditReconciler.reconcile(r,Map.of(5,page()),w);
  assertEquals(3,r.at("/totals/completed").asInt());assertEquals(6,r.at("/totals/inProgress").asInt());assertEquals(3,r.at("/totals/inProgressByTerm/FA 2026").asInt());assertTrue(r.at("/totals/evidence/verified").asBoolean());
 }
 @Test void incompleteOrDuplicateCourseTableDoesNotGuess(){
  for(String text:new String[]{page().replace("3.00 A","2.00 A"),page().replace("FA 2026 CMPSC 221","SP 2026 CMPSC 132"),page().replace("Knowledge Domain Flexibility","")}){
   var r=mapper.createObjectNode();r.putObject("totals").put("completed",9).put("inProgress",3);
   DegreeCreditReconciler.reconcile(r,Map.of(1,text),r.putArray("warnings"));assertTrue(r.at("/totals/completed").isNull());assertTrue(r.at("/totals/inProgress").isNull());
  }
 }
 @Test @EnabledIfSystemProperty(named="auditPdf",matches=".+") void localPdfReconcilesWithoutCommittingPrivateFixture()throws Exception{
  var pages=new PdfReader().read(Files.readAllBytes(Path.of(System.getProperty("auditPdf"))));
  var r=mapper.createObjectNode();r.putObject("totals");DegreeCreditReconciler.reconcile(r,pages,r.putArray("warnings"));
  assertEquals("degree_table_reconciled",r.at("/totals/evidence/method").asText());
  System.out.println("Verified degree totals: required="+r.at("/totals/required")+", earned="+r.at("/totals/completed")+", IP="+r.at("/totals/inProgress")+", additional="+r.at("/totals/evidence/neededAfterInProgress"));
 }
}
