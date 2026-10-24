package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CatalogServiceTest {
 final ObjectMapper mapper=new ObjectMapper();
 CatalogService catalog;
 final Map<Integer,String> pages=Map.of(1,"Computer Science, B.S. University Park Requirement year 2026–27 Department List");
 @BeforeEach void setUp()throws Exception{catalog=new CatalogService(mapper);}
 ObjectNode report()throws Exception{return (ObjectNode)mapper.readTree("""
 {"major":"Computer Science, B.S.","campus":"University Park","catalogYear":"2026-27","totals":{"required":120,"completed":60,"inProgress":15},"warnings":[],"requirements":[{"title":"Department List","required":12,"completed":3,"inProgress":0,"status":"remaining","description":"From report","courses":[],"evidence":{"page":1,"quote":"Department List"}}]}
 """);}
 @Test void attachesCurrentRuleWithoutChangingReportCredits()throws Exception{
  var r=report();var before=r.path("totals").deepCopy();catalog.enrich(r,pages);
  assertEquals(before,r.path("totals"));assertEquals(12,r.at("/requirements/0/required").asInt());
  assertEquals(6,r.at("/requirements/0/catalogMatch/requiredCredits").asInt());
  assertTrue(r.at("/requirements/0/catalogMatch/creditConflict").asBoolean());
  assertTrue(r.at("/catalog/totalCreditConflict").asBoolean());
  assertFalse(r.at("/catalog/automaticAudit").asBoolean());
  assertTrue(r.at("/requirements/0/catalogMatch/rule/requires_review").asBoolean());
 }
 @Test void scopeCannotComeFromUserIntentOrWrongYear()throws Exception{
  for(String field:List.of("major","campus","catalogYear")){
   var r=report();r.put(field,"unknown");r.put("intent","Computer Science University Park 2026-27");catalog.enrich(r,pages);
   assertEquals("scope_not_matched",r.at("/catalog/status").asText());assertFalse(r.at("/requirements/0").has("catalogMatch"));
  }
  var r=report();r.put("catalogYear","2024-25");catalog.enrich(r,pages);assertEquals("scope_not_matched",r.at("/catalog/status").asText());
 }
 @Test void uncorroboratedMetadataAndLegacyReportsStayUnverified()throws Exception{
  var r=report();catalog.enrich(r,Map.of(1,"Random document"));assertEquals("scope_unverified",r.at("/catalog/status").asText());
  var old=report();assertEquals("scope_unverified",catalog.forRead(old).at("/catalog/status").asText());assertFalse(old.has("catalog"));
 }
 @Test void supportingIsNotConvertedAndUnknownLabelStaysUnknown()throws Exception{
  var r=report();((ObjectNode)r.at("/requirements/0")).put("title","Supportive Electives");catalog.enrich(r,pages);
  assertEquals("no_current_equivalent",r.at("/requirements/0/catalogMatch/status").asText());assertFalse(r.at("/requirements/0/catalogMatch").has("rule"));
  ((ObjectNode)r.at("/requirements/0")).put("title","List A");catalog.enrich(r,pages);assertEquals("unmatched",r.at("/requirements/0/catalogMatch/status").asText());
 }
 @Test void courseOptionsHaveOfficialSourcesAndNoEligibilityClaim()throws Exception{
  var r=report();((ObjectNode)r.at("/requirements/0")).put("title","Technical Electives");catalog.enrich(r,pages);
  var m=r.at("/requirements/0/catalogMatch");assertEquals(27,m.path("courseOptions").size());assertEquals(18,m.path("requiredCredits").asInt());
  assertTrue(m.path("courseOptions").path(0).path("sourceUrl").asText().startsWith("https://bulletins.psu.edu/"));assertFalse(m.path("automaticApplicationAllowed").asBoolean());
 }
 @Test void uploadPersistsEnrichmentAndChatReceivesIt()throws Exception{
  var pdf=mock(PdfReader.class);var gemini=mock(GeminiService.class);var router=mock(ModelRouter.class);var repo=mock(ReportRepository.class);
  var controller=new ReportController(pdf,gemini,router,new AnalysisValidator(),repo,catalog);
  var jwt=Jwt.withTokenValue("test").header("alg","RS256").subject("owner").build();
  when(pdf.read(any())).thenReturn(pages);when(gemini.analyze(any(),any())).thenReturn(report());
  var result=controller.upload(jwt,new MockMultipartFile("file","test.pdf","application/pdf",new byte[]{1}),"");
  var id=UUID.fromString(result.path("id").asText());verify(repo).save(eq(id),eq("owner"),eq("test.pdf"),argThat(r->r.at("/catalog/status").asText().equals("matched_with_review")));
  when(repo.get(id,"owner")).thenReturn(result);assertEquals(result,controller.get(jwt,id));
  when(repo.history(id)).thenReturn(List.of());when(router.chat(any(),any(),any())).thenReturn(new ModelRouter.Answer("Synthetic explanation","gemini"));
  assertEquals("Synthetic explanation",controller.chat(jwt,id,new ReportController.Question("Explain Department List")).get("answer"));
  verify(router).chat(argThat(r->r.at("/requirements/0/catalogMatch/requiredCredits").asInt()==6),eq("Explain Department List"),eq(List.of()));
  var input=GeminiService.chatInput(controller.get(jwt,id),"Explain Department List",List.of());assertTrue(input.contains("concept_match_credit_conflict"));assertTrue(input.contains("bulletins.psu.edu"));
 }
}
