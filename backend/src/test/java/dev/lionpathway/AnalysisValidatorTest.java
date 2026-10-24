package dev.lionpathway;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AnalysisValidatorTest {
 private final ObjectMapper mapper=new ObjectMapper();private final AnalysisValidator validator=new AnalysisValidator();
 @Test void invalidCreditsAndUnsupportedEvidenceAreFlagged()throws Exception{
  var parsed=mapper.readTree("""
   {"totals":{"required":120,"completed":-1,"inProgress":null},"requirements":[{"title":"List A","description":"Choose 6 credits","status":"complete","required":6,"completed":6,"inProgress":0,"courses":[],"evidence":{"page":9,"quote":"Invented quote"}}],"warnings":[]}
   """);var result=validator.validate(parsed,Map.of(1,"Actual page"));assertTrue(result.path("totals").path("completed").isNull());assertTrue(result.path("totals").path("inProgress").isNull());assertEquals("review",result.path("requirements").get(0).path("status").asText());assertFalse(result.path("requirements").get(0).path("evidence").path("verified").asBoolean());
 }
 @Test void matchingEvidenceSurvivesWhitespaceDifferences()throws Exception{var parsed=mapper.readTree("""
 {"major":"Computer Science","totals":{"required":120,"completed":72,"inProgress":15},"requirements":[{"title":"List A","description":"Choose six credits","status":"remaining","courses":[],"evidence":{"page":1,"quote":"Choose six credits"}}]}
 """);assertTrue(validator.validate(parsed,Map.of(1,"Section A\nChoose   six credits\n")).path("requirements").get(0).path("evidence").path("verified").asBoolean());}
 @Test void malformedModelOutputIsRejected()throws Exception{assertThrows(IllegalArgumentException.class,()->validator.validate(mapper.readTree("{}"),Map.of()));}
}
