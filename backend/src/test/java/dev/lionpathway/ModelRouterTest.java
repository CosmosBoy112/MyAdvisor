package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ModelRouterTest {
 private final ObjectMapper mapper=new ObjectMapper();
 @Test void classifiesPlanningAsComplex(){assertTrue(ModelRouter.complex("Can you plan my next two semesters?"));assertTrue(ModelRouter.complex("수학 부전공 추가하면 학기별 계획 짜줘"));assertTrue(ModelRouter.complex("x".repeat(401)));}
 @Test void classifiesLookupsAsSimple(){assertFalse(ModelRouter.complex("How many credits are left?"));assertFalse(ModelRouter.complex("Explain Department List"));assertFalse(ModelRouter.complex("Is my minor included?"));}
 @Test void complexQuestionUsesLuminal(){var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);var report=mapper.createObjectNode();when(luminal.configured()).thenReturn(true);when(luminal.chat(report,"Plan my semesters",List.of())).thenReturn("plan");var answer=new ModelRouter(gemini,luminal).chat(report,"Plan my semesters",List.of());assertEquals(new ModelRouter.Answer("plan","luminal"),answer);verifyNoInteractions(gemini);}
 @Test void simpleQuestionUsesGemini(){var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);var report=mapper.createObjectNode();when(luminal.configured()).thenReturn(true);when(gemini.chat(report,"How many credits are left?",List.of())).thenReturn("33");assertEquals("gemini",new ModelRouter(gemini,luminal).chat(report,"How many credits are left?",List.of()).provider());verify(luminal,never()).chat(any(),any(),any());}
 @Test void fallsBackToGeminiWhenLuminalFails(){var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);var report=mapper.createObjectNode();when(luminal.configured()).thenReturn(true);when(luminal.chat(any(),any(),any())).thenThrow(new UpstreamFailure("down"));when(gemini.chat(report,"Plan my semesters",List.of())).thenReturn("fallback");assertEquals(new ModelRouter.Answer("fallback","gemini"),new ModelRouter(gemini,luminal).chat(report,"Plan my semesters",List.of()));}
 @Test void skipsLuminalWhenNotConfigured(){var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);var report=mapper.createObjectNode();when(gemini.chat(report,"Plan my semesters",List.of())).thenReturn("g");assertEquals("gemini",new ModelRouter(gemini,luminal).chat(report,"Plan my semesters",List.of()).provider());verify(luminal,never()).chat(any(),any(),any());}
}
