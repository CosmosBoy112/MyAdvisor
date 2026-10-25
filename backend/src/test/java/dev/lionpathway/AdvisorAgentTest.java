package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class AdvisorAgentTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private final GeminiService gemini=mock(GeminiService.class);private final AdvisorTools tools=mock(AdvisorTools.class);
 private final AdvisorAgent agent=new AdvisorAgent(gemini,tools,mapper,true);
 private final ObjectNode report=mapper.createObjectNode().put("major","Data Sciences (BS)");
 private JsonNode content(String json)throws Exception{return mapper.readTree(json);}
 private void stubTools(){
  var turn=new AdvisorTools.Turn(report,List.of(),Optional.empty(),true);
  when(tools.turn(any(),any(),any(),any())).thenReturn(turn);when(tools.declarations(any())).thenReturn(mapper.createArrayNode());
  when(tools.call(any(),any(),any())).thenAnswer(i->mapper.createObjectNode().put("ok",true).put("tool",(String)i.getArgument(0)));
  when(tools.error(any(),any())).thenAnswer(i->mapper.createObjectNode().put("ok",false));
 }

 @Test void callsRequestedToolsThenAnswersFromTheirResults()throws Exception{
  stubTools();
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any()))
   .thenReturn(content("{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"id\":\"c1\",\"name\":\"check_prerequisites\",\"args\":{\"code\":\"CMPSC 465\"}},\"thoughtSignature\":\"sig\"},{\"functionCall\":{\"name\":\"get_course\",\"args\":{\"code\":\"CMPSC 465\"}}}]}"))
   .thenReturn(content("{\"parts\":[{\"text\":\"secret reasoning\",\"thought\":true},{\"text\":\"Once MATH 230 is passed you can take CMPSC 465.\"}]}"));
  var result=agent.chat(report,"Can I take CMPSC 465?",List.of());
  assertEquals("Once MATH 230 is passed you can take CMPSC 465.",result.text());
  assertEquals(2,result.toolCalls().size());assertEquals("check_prerequisites",result.toolCalls().get(0).path("name").asText());
  verify(tools).call(eq("check_prerequisites"),argThat(a->a.path("code").asText().equals("CMPSC 465")),any());
  var captor=org.mockito.ArgumentCaptor.forClass(JsonNode.class);
  verify(gemini,times(2)).agentTurn(any(),captor.capture(),any(),anyBoolean(),any());
  var contents=captor.getAllValues().get(1);
  assertEquals(3,contents.size(),"question, model tool calls, tool responses");
  assertEquals("sig",contents.at("/1/parts/0/thoughtSignature").asText(),"thought signatures go back verbatim");
  assertEquals("c1",contents.at("/2/parts/0/functionResponse/id").asText());assertEquals("get_course",contents.at("/2/parts/1/functionResponse/name").asText());
 }
 @Test void lastStepForcesATextAnswer()throws Exception{
  stubTools();
  var call=content("{\"parts\":[{\"functionCall\":{\"name\":\"get_course\",\"args\":{\"code\":\"DS 200\"}}}]}");
  var flags=new ArrayList<Boolean>();
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenAnswer(i->{boolean allowed=i.getArgument(3);flags.add(allowed);return allowed?call:content("{\"parts\":[{\"text\":\"done\"}]}");});
  assertEquals("done",agent.chat(report,"Tell me everything",List.of()).text());
  assertEquals(AdvisorAgent.MAX_MODEL_CALLS,flags.size());assertFalse(flags.getLast());
 }
 @Test void toolCallsBeyondTheLimitAreRefused()throws Exception{
  stubTools();
  var many=new StringBuilder("{\"parts\":[");for(int i=0;i<10;i++)many.append(i==0?"":",").append("{\"functionCall\":{\"name\":\"get_course\",\"args\":{}}}");many.append("]}");
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(content(many.toString())).thenReturn(content("{\"parts\":[{\"text\":\"ok\"}]}"));
  var result=agent.chat(report,"q",List.of());
  verify(tools,times(AdvisorAgent.MAX_TOOL_CALLS)).call(any(),any(),any());assertEquals(10,result.toolCalls().size());
 }
 @Test void blankAnswerIsAnUpstreamFailure()throws Exception{
  stubTools();when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(content("{\"parts\":[{\"text\":\"  \"}]}"));
  assertThrows(UpstreamFailure.class,()->agent.chat(report,"q",List.of()));
 }
 @Test void contextIsASummaryWithoutRequirementTrees()throws Exception{
  var full=(ObjectNode)mapper.readTree("{\"major\":\"Data Sciences (BS)\",\"totals\":{\"completed\":60},\"requirements\":[{\"title\":\"List A\",\"status\":\"in_progress\",\"courses\":[\"long list\"],\"evidence\":{\"quote\":\"q\"}}],\"courseInventory\":{\"courseCount\":4,\"courses\":[{\"code\":\"DS 200\"}]}}");
  String input=agent.input(full,new AdvisorTools.Turn(full,List.of(),Optional.empty(),false),"Can I take DS 300?",List.of(Map.of("role","user","content","hi")));
  assertTrue(input.contains("\"title\":\"List A\""));assertFalse(input.contains("long list"));assertTrue(input.contains("DS 200"),"course rows are part of the profile");
  assertTrue(input.contains("major_not_in_catalog"));assertTrue(input.endsWith("Can I take DS 300?"));
 }
 @Test void controllerFallsBackToFixedContextChatWhenTheAgentFails()throws Exception{
  var repo=mock(ReportRepository.class);var router=mock(ModelRouter.class);var catalog=mock(CatalogService.class);var catalogDb=mock(CatalogDbService.class);var failing=mock(AdvisorAgent.class);
  var id=UUID.randomUUID();var jwt=Jwt.withTokenValue("t").header("alg","RS256").subject("auth0|owner").build();
  when(repo.get(id,"auth0|owner")).thenReturn(mapper.createObjectNode());when(catalog.forRead(any())).thenAnswer(i->i.getArgument(0));when(repo.history(id)).thenReturn(List.of());
  when(failing.enabled()).thenReturn(true);when(failing.chat(any(),any(),any(),any(),any(),any())).thenThrow(new AnalysisTimeout("slow"));
  when(catalogDb.context(any(),any())).thenReturn(mapper.createObjectNode().put("status","matched"));when(router.fallbackChat(any(),any(),any())).thenReturn(new ModelRouter.Answer("fixed answer","gemini"));
  var controller=new ReportController(mock(PdfReader.class),gemini,router,new AnalysisValidator(),repo,catalog,catalogDb,null,failing);
  var response=controller.chat(jwt,id,new ReportController.Question("Can I take CMPSC 465?"));
  assertEquals("fixed answer",response.get("answer"));assertEquals("gemini",response.get("provider"));
  verify(router).fallbackChat(argThat(r->r.has("catalogDb")),eq("Can I take CMPSC 465?"),any());verify(router,never()).chat(any(),any(),any());verify(repo).conversation(eq(id),any(),any());
 }
 @Test void controllerReturnsAgentAnswerAndToolTrace()throws Exception{
  var repo=mock(ReportRepository.class);var router=mock(ModelRouter.class);var catalog=mock(CatalogService.class);var working=mock(AdvisorAgent.class);
  var id=UUID.randomUUID();var jwt=Jwt.withTokenValue("t").header("alg","RS256").subject("auth0|owner").build();
  when(repo.get(id,"auth0|owner")).thenReturn(mapper.createObjectNode());when(catalog.forRead(any())).thenAnswer(i->i.getArgument(0));
  var trace=mapper.createArrayNode();trace.addObject().put("name","get_course").put("ok",true);
  when(working.enabled()).thenReturn(true);when(working.chat(any(),any(),any(),eq("auth0|owner"),eq(id),any())).thenReturn(new AdvisorAgent.Result("agent answer",null,trace,"luminal-agent"));
  var response=new ReportController(mock(PdfReader.class),gemini,router,new AnalysisValidator(),repo,catalog,mock(CatalogDbService.class),null,working).chat(jwt,id,new ReportController.Question("What is DS 200?"));
  assertEquals("agent answer",response.get("answer"));assertEquals("luminal-agent",response.get("provider"));assertEquals(trace,response.get("toolCalls"));
  verifyNoInteractions(router);
 }

 private final LuminalService luminal=mock(LuminalService.class);
 private AdvisorAgent luminalFirst(){return new AdvisorAgent(gemini,luminal,tools,mapper,true,"luminal");}
 @Test void luminalRunsTheLoopWithOpenAiToolMessages()throws Exception{
  stubTools();when(luminal.configured()).thenReturn(true);
  when(luminal.agentTurn(any(),any(),anyBoolean(),any()))
   .thenReturn(content("{\"role\":\"assistant\",\"content\":null,\"reasoning_content\":\"think\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"check_prerequisites\",\"arguments\":\"{\\\"code\\\":\\\"CMPSC 465\\\"}\"}}]}"))
   .thenReturn(content("{\"role\":\"assistant\",\"content\":\"Not yet: CMPSC 122 or 132 is missing.\"}"));
  var result=luminalFirst().chat(report,"Can I take CMPSC 465?",List.of());
  assertEquals("luminal-agent",result.provider());assertEquals("Not yet: CMPSC 122 or 132 is missing.",result.text());
  verify(tools).call(eq("check_prerequisites"),argThat(a->a.path("code").asText().equals("CMPSC 465")),any());verifyNoInteractions(gemini);
  var captor=org.mockito.ArgumentCaptor.forClass(JsonNode.class);verify(luminal,times(2)).agentTurn(captor.capture(),any(),anyBoolean(),any());
  var messages=captor.getAllValues().get(1);
  assertEquals("system",messages.at("/0/role").asText());assertEquals("user",messages.at("/1/role").asText());
  assertEquals("think",messages.at("/2/reasoning_content").asText(),"assistant tool-call message goes back verbatim");
  assertEquals("tool",messages.at("/3/role").asText());assertEquals("call_1",messages.at("/3/tool_call_id").asText());
  assertTrue(mapper.readTree(messages.at("/3/content").asText()).path("ok").asBoolean(),"tool content is the JSON envelope as a string");
 }
 @Test void luminalFailureFallsBackToAGeminiRun()throws Exception{
  stubTools();when(luminal.configured()).thenReturn(true);when(luminal.agentTurn(any(),any(),anyBoolean(),any())).thenThrow(new UpstreamFailure("rejected"));
  when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(content("{\"parts\":[{\"text\":\"gemini answer\"}]}"));
  var result=luminalFirst().chat(report,"q",List.of());assertEquals("gemini-agent",result.provider());assertEquals("gemini answer",result.text());
  verify(tools,times(2)).turn(any(),any(),any(),any());
 }
 @Test void unconfiguredLuminalGoesStraightToGemini()throws Exception{
  stubTools();when(gemini.agentTurn(any(),any(),any(),anyBoolean(),any())).thenReturn(content("{\"parts\":[{\"text\":\"g\"}]}"));
  assertEquals("gemini-agent",luminalFirst().chat(report,"q",List.of()).provider());verify(luminal,never()).agentTurn(any(),any(),anyBoolean(),any());
  assertThrows(IllegalArgumentException.class,()->new AdvisorAgent(gemini,luminal,tools,mapper,true,"openai"));
 }
 @Test void malformedToolArgumentsBecomeToolErrors()throws Exception{
  var step=AdvisorAgent.openAiStep(content("{\"content\":\"\",\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"get_course\",\"arguments\":\"{code: CMPSC\"}},{\"id\":\"b\",\"function\":{\"name\":\"get_student_record\",\"arguments\":{\"section\":\"summary\"}}},{\"id\":\"c\",\"function\":{\"name\":\"get_policies\",\"arguments\":\"\"}}]}"),mapper);
  assertEquals(3,step.calls().size());assertNotNull(step.calls().get(0).argumentError());
  assertNull(step.calls().get(1).argumentError());assertEquals("summary",step.calls().get(1).args().path("section").asText());
  assertNull(step.calls().get(2).argumentError(),"empty arguments mean no arguments");
  stubTools();when(luminal.configured()).thenReturn(true);
  when(luminal.agentTurn(any(),any(),anyBoolean(),any())).thenReturn(content("{\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"get_course\",\"arguments\":\"not json\"}}]}")).thenReturn(content("{\"content\":\"done\"}"));
  assertEquals("done",luminalFirst().chat(report,"q",List.of()).text());
  verify(tools).error(eq("INVALID_ARGUMENT"),any());verify(tools,never()).call(any(),any(),any());
 }
 @Test void declarationsBecomeOpenAiFunctionTools()throws Exception{
  var specs=LuminalService.toolSpecs(content("[{\"name\":\"get_course\",\"description\":\"d\",\"parameters\":{\"type\":\"object\"}}]"),mapper);
  assertEquals("function",specs.at("/0/type").asText());assertEquals("get_course",specs.at("/0/function/name").asText());assertEquals("object",specs.at("/0/function/parameters/type").asText());
 }
}
