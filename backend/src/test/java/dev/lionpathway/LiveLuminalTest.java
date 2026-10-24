package dev.lionpathway;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Explicit opt-in: uses synthetic data and must succeed on Luminal, never Gemini fallback. */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LUMINAL_TEST", matches="1")
class LiveLuminalTest {
 @Test void planningQuestionReceivesAnActualLuminalAnswer() throws Exception {
  var env=Dotenv.configure().ignoreIfMissing().load();
  var mapper=new ObjectMapper();
  var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
  assertTrue(luminal.configured(),"Set LUMINAL_API_KEY in the backend environment before running this test");
  var gemini=mock(GeminiService.class);
  var report=mapper.readTree("""
   {"major":"Synthetic test major","minor":null,"totals":{"required":120,"completed":90,"inProgress":0},
    "requirements":[],"warnings":["Synthetic fixture only. Prerequisites and course offerings are unknown."]}
   """);
  long started=System.nanoTime();
  var answer=new ModelRouter(gemini,luminal).chat(report,"Compare a two-semester credit plan with a three-semester credit plan. Explain the missing information. Keep the answer under 100 words.",List.of());
  assertEquals("luminal",answer.provider(),"Gemini fallback does not verify the Luminal connection");
  assertNotNull(answer.text());assertFalse(answer.text().isBlank());
  verifyNoInteractions(gemini);
  System.out.println("LIVE_LUMINAL: authenticated API, configured model, nonempty answer, and planning routing passed in "+(System.nanoTime()-started)/1_000_000+" ms (synthetic input only)");
 }
}
