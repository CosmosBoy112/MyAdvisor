package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AgentProgressTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private static final String RID="0f8c3c1e-6c55-4d7a-9a51-3c1d2b4e5f60";

 @Test void stepsAreReadableOnlyByTheirSubject(){
  var progress=new AgentProgress();var listener=progress.start("auth0|a",RID);
  listener.step("llm","plan");listener.step("tool","check_prerequisites");
  var read=progress.read("auth0|a",RID).orElseThrow();
  assertEquals(2,((List<?>)read.get("steps")).size());assertEquals(false,read.get("done"));
  assertTrue(progress.read("auth0|b",RID).isEmpty(),"another user cannot read it");
  progress.finish("auth0|a",RID);assertEquals(true,progress.read("auth0|a",RID).orElseThrow().get("done"));
  assertTrue(progress.read("auth0|a","unknown").isEmpty());
 }
 @Test void agentReportsModelCallsToolsAndFallback()throws Exception{
  var gemini=mock(GeminiService.class);var luminal=mock(LuminalService.class);var tools=mock(AdvisorTools.class);
  when(luminal.configured()).thenReturn(true);when(luminal.agentTurn(any(),any(),anyBoolean(),any())).thenThrow(new UpstreamFailure("down"));
  var report=mapper.createObjectNode();
  when(tools.turn(any(),any(),any(),any())).thenReturn(new AdvisorTools.Turn(report,List.of(),Optional.empty(),false));when(tools.declarations(any())).thenReturn(mapper.createArrayNode());
  when(tools.call(any(),any(),any())).thenReturn(mapper.createObjectNode().put("ok",true));
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any()))
   .thenReturn(mapper.readTree("{\"parts\":[{\"functionCall\":{\"name\":\"check_prerequisites\",\"args\":{\"code\":\"CMPSC 465\"}}}]}"))
   .thenReturn(mapper.readTree("{\"parts\":[{\"text\":\"done\"}]}"));
  var steps=new ArrayList<String>();
  new AdvisorAgent(gemini,luminal,tools,mapper,true,"luminal").chat(report,"q",List.of(),null,null,(k,d)->steps.add(k+":"+d));
  assertEquals(List.of("llm:plan","fallback:gemini","llm:plan","tool:check_prerequisites","llm:plan"),steps);
 }
 @Test void controllerTracksTheRequestAndAlwaysFinishesIt()throws Exception{
  var repo=mock(ReportRepository.class);var catalog=mock(CatalogService.class);var agent=mock(AdvisorAgent.class);var progress=spy(new AgentProgress());
  var id=UUID.randomUUID();var jwt=Jwt.withTokenValue("t").header("alg","RS256").subject("auth0|owner").build();
  when(repo.get(id,"auth0|owner")).thenReturn(mapper.createObjectNode());when(catalog.forRead(any())).thenAnswer(i->i.getArgument(0));
  when(agent.enabled()).thenReturn(true);
  when(agent.chat(any(),any(),any(),any(),any(),any())).thenAnswer(i->{((AgentProgress.Listener)i.getArgument(5)).step("tool","get_course");return new AdvisorAgent.Result("ok",null,mapper.createArrayNode(),"luminal-agent");});
  var controller=new ReportController(mock(PdfReader.class),mock(GeminiService.class),mock(ModelRouter.class),new AnalysisValidator(),repo,catalog,mock(CatalogDbService.class),null,agent);controller.setProgress(progress);
  controller.chat(jwt,id,new ReportController.Question("What is DS 200?","en",true,RID));
  var read=controller.chatProgress(jwt,id,RID);
  assertEquals(true,read.get("done"));assertTrue(read.get("steps").toString().contains("get_course"));
  var other=Jwt.withTokenValue("t").header("alg","RS256").subject("auth0|other").build();
  assertEquals(List.of(),controller.chatProgress(other,id,RID).get("steps"),"another user sees nothing");
 }
}
