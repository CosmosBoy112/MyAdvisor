package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ReportEndpointsTest {
 private Jwt token(){return Jwt.withTokenValue("test-token").header("alg","RS256").subject("auth0|test-owner").build();}
 @Test void listUsesAuthenticatedSubject(){var repo=mock(ReportRepository.class);var controller=new ReportController(mock(PdfReader.class),mock(GeminiService.class),mock(ModelRouter.class),new AnalysisValidator(),repo,mock(CatalogService.class));when(repo.list("auth0|test-owner")).thenReturn(List.of());assertTrue(controller.list(token()).isEmpty());verify(repo).list("auth0|test-owner");}
 @Test void historyChecksOwnershipBeforeReadingMessages(){var repo=mock(ReportRepository.class);var controller=new ReportController(mock(PdfReader.class),mock(GeminiService.class),mock(ModelRouter.class),new AnalysisValidator(),repo,mock(CatalogService.class));var id=UUID.randomUUID();when(repo.get(id,"auth0|test-owner")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));assertThrows(ResponseStatusException.class,()->controller.messages(token(),id));verify(repo,never()).history(any());}
 @Test void otherUsersCannotTriggerPaidChat(){var repo=mock(ReportRepository.class);var ai=mock(ModelRouter.class);var controller=new ReportController(mock(PdfReader.class),mock(GeminiService.class),ai,new AnalysisValidator(),repo,mock(CatalogService.class));var id=UUID.randomUUID();when(repo.get(id,"auth0|test-owner")).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));assertThrows(ResponseStatusException.class,()->controller.chat(token(),id,new ReportController.Question("Explain this")));verifyNoInteractions(ai);}
}
