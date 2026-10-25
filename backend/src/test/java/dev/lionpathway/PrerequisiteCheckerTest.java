package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PrerequisiteCheckerTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private JsonNode json(String text)throws Exception{return mapper.readTree(text);}
 private JsonNode rows(String... rows)throws Exception{return json("["+String.join(",",rows)+"]");}
 private String row(String code,String grade){return "{\"code\":\""+code+"\",\"term\":\"FA 2025\",\"grade\":\""+grade+"\",\"credits\":3,\"requiresReview\":false}";}
 private String check(String tree,JsonNode courses)throws Exception{return PrerequisiteChecker.evaluate(json(tree),courses,Map.of(),mapper).path("status").asText();}

 @Test void allNeedsEveryCourseAndRespectsMinimumGrade()throws Exception{
  String tree="{\"type\":\"ALL\",\"children\":[{\"type\":\"COURSE\",\"course\":\"CMPSC 360\",\"min_grade\":\"C\"},{\"type\":\"COURSE\",\"course\":\"CMPSC 221\"}]}";
  assertEquals("met",check(tree,rows(row("CMPSC 360","B-"),row("CMPSC 221","D"))));
  assertEquals("not_met",check(tree,rows(row("CMPSC 360","D"),row("CMPSC 221","A"))),"D is below a C minimum");
  assertEquals("not_met",check(tree,rows(row("CMPSC 360","A"))),"missing course");
  assertEquals("in_progress",check(tree,rows(row("CMPSC 360","IP"),row("CMPSC 221","A"))));
 }
 @Test void anyTakesTheBestBranch()throws Exception{
  String tree="{\"type\":\"ANY\",\"children\":[{\"type\":\"COURSE\",\"course\":\"MATH 141\"},{\"type\":\"COURSE\",\"course\":\"MATH 140H\"}]}";
  assertEquals("met",check(tree,rows(row("MATH 141","F"),row("MATH 140H","B"))));
  assertEquals("not_met",check(tree,rows(row("MATH 141","W"))),"withdrawn courses are not earned");
 }
 @Test void unparsedTextAndStandingAreUnknownNotGuessed()throws Exception{
  assertEquals("unknown",check("{\"type\":\"TEXT\",\"unparsed_text\":\"permission of program\"}",rows()));
  assertEquals("unknown",check("{\"type\":\"ALL\",\"children\":[{\"type\":\"COURSE\",\"course\":\"STAT 200\"},{\"type\":\"STANDING\",\"min_semester\":5}]}",rows(row("STAT 200","A"))));
  assertEquals("not_met",check("{\"type\":\"ALL\",\"children\":[{\"type\":\"COURSE\",\"course\":\"STAT 200\"},{\"type\":\"TEXT\"}]}",rows()),"a failed branch decides ALL even next to unknown wording");
 }
 @Test void transferCreditMeetsPlainPrerequisitesButNotUnverifiedGrades()throws Exception{
  assertEquals("met",check("{\"type\":\"COURSE\",\"course\":\"ENGL 15\"}",rows(row("ENGL 15","TR"))));
  assertEquals("unknown",check("{\"type\":\"COURSE\",\"course\":\"ENGL 15\",\"min_grade\":\"C\"}",rows(row("ENGL 15","TR"))));
 }
 @Test void corequisitesAcceptCurrentEnrollment()throws Exception{
  assertEquals("met",check("{\"type\":\"COREQ\",\"course\":\"PHYS 211\"}",rows(row("PHYS 211","IP"))));
  var result=PrerequisiteChecker.evaluate(json("{\"type\":\"COURSE\",\"course\":\"PHYS 211\",\"concurrent_ok\":true}"),rows(),Map.of(),mapper);
  assertEquals("not_met",result.path("status").asText());assertTrue(result.path("tree").path("may_take_concurrently").asBoolean());
  assertTrue(result.at("/tree/meaning").asText().contains("does not have to be taken together"));
 }
 @Test void crossListedCodesAndConflictingRows()throws Exception{
  var tree=json("{\"type\":\"COURSE\",\"course\":\"STAT 318\"}");
  assertEquals("met",PrerequisiteChecker.evaluate(tree,rows(row("MATH 318","B")),Map.of("STAT 318",Set.of("MATH 318")),mapper).path("status").asText());
  var conflicting=json("[{\"code\":\"STAT 318\",\"grade\":\"B\",\"requiresReview\":true}]");
  assertEquals("unknown",PrerequisiteChecker.evaluate(tree,conflicting,Map.of(),mapper).path("status").asText());
 }
 @Test void reportsMatchedRecordAndMissingTree()throws Exception{
  var result=PrerequisiteChecker.evaluate(json("{\"type\":\"COURSE\",\"course\":\"DS 200\"}"),rows(row("DS 200","A-")),Map.of(),mapper);
  assertEquals("A-",result.at("/tree/matched_record/grade").asText());assertEquals("FA 2025",result.at("/tree/matched_record/term").asText());
  assertEquals("no_prerequisites_in_database",PrerequisiteChecker.evaluate(null,rows(),Map.of(),mapper).path("status").asText());
 }
}
