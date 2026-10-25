package dev.lionpathway;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
class RequirementPlanningTest {
 @Test void fullPlanIsCoveredButPartialUnknownAndReviewAreNot(){
  var m=new ObjectMapper();var r=m.createObjectNode().put("status","in_progress").put("required",6).put("completed",3).put("inProgress",3);
  assertTrue(RequirementPlanning.covered(r));assertEquals("in_progress",r.path("status").asText());
  r.put("inProgress",2);assertFalse(RequirementPlanning.covered(r));r.put("inProgress",3).put("status","review");assertFalse(RequirementPlanning.covered(r));r.put("status","in_progress").putNull("completed");assertFalse(RequirementPlanning.covered(r));
 }
}
