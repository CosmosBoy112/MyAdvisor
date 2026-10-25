package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class WebResearchServiceTest {
 final ObjectMapper mapper=new ObjectMapper();
 final WebResearchService service=new WebResearchService("","test-model",mapper);
 @Test void searchesRecommendationsAndUncertainScopeButNotCreditArithmetic()throws Exception{
  var report=mapper.readTree("{\"catalogDb\":{\"status\":\"matched\"}}");
  assertTrue(WebResearchService.shouldSearch(report,"CMPSC 465 교수 후기 알려줘"));
  assertFalse(WebResearchService.shouldSearch(report,"How many credits are left?"));
  assertTrue(WebResearchService.shouldSearch(mapper.createObjectNode(),"What can I take instead?"));
  ((com.fasterxml.jackson.databind.node.ObjectNode)report).put("webSearchEnabled",false);
  assertFalse(WebResearchService.shouldSearch(report,"Search Reddit for CMPSC 465"));
 }
 @Test void redactsIdentifiersAndCarriesOnlyCourseCodesFromHistory(){
  String query=WebResearchService.publicQuestion("Email me at a@example.com ID 123456789 https://private.example/report",List.of(Map.of("content","Student Jane Private has GPA 2.1 and takes CMPSC 465")));
  assertFalse(query.contains("a@example.com"));assertFalse(query.contains("123456789"));assertFalse(query.contains("private.example"));assertFalse(query.contains("Jane"));assertFalse(query.contains("2.1"));assertTrue(query.contains("CMPSC 465"));
 }
 @Test void onlyValidGroundedSegmentsBecomeEvidenceAndPreserveSourceIds()throws Exception{
  var response=mapper.readTree("""
  {"candidates":[{"content":{"parts":[{"text":"Ungrounded invented rating 5/5"}]},"groundingMetadata":{
  "groundingChunks":[{"web":{"uri":"javascript:alert(1)","title":"bad"}},{"web":{"uri":"https://bulletins.psu.edu/course","title":"Bulletin"}},{"web":{"uri":"https://www.reddit.com/r/PennStateUniversity/comments/example","title":"Experience"}}],
  "groundingSupports":[{"segment":{"text":"Official rule"},"groundingChunkIndices":[1]},{"segment":{"text":"Subjective experience"},"groundingChunkIndices":[2]},{"segment":{"text":"Not supported"},"groundingChunkIndices":[0,99]}],
  "searchEntryPoint":{"renderedContent":"<div>Search suggestions</div>"}}}]}
  """);
  var result=service.parse(response);
  assertEquals("searched",result.path("status").asText());assertEquals(2,result.path("sources").size());assertEquals("W2",result.path("sources").path(0).path("id").asText());assertEquals("official",result.path("sources").path(0).path("kind").asText());assertEquals("community",result.path("sources").path(1).path("kind").asText());assertEquals(2,result.path("excerpts").size());assertFalse(result.toString().contains("5/5"));assertFalse(result.toString().contains("Not supported"));assertTrue(result.has("searchEntryPoint"));
 }
 @Test void emptyGroundingAndMissingKeyDoNotClaimSuccessfulSearch()throws Exception{
  assertEquals("no_results",service.parse(mapper.readTree("{\"candidates\":[{}]}")).path("status").asText());
  assertEquals("unavailable",service.search("professor reviews",List.of()).path("status").asText());
  assertNull(WebResearchService.safeUrl("https://user:password@example.com/a"));assertNull(WebResearchService.safeUrl("http://example.com"));assertNull(WebResearchService.safeUrl("https://127.0.0.1/a"));assertEquals("web",WebResearchService.kind("https://psu.edu.evil.example/a"));
 }
 @Test void citationsAndSearchSuggestionsSurviveHistoryRoundTrip()throws Exception{
  var web=service.status("searched");web.put("searchEntryPoint","<div>Suggestions</div>");
  var stored=ChatMessageCodec.encode("Tentative advice [W1]",web);
  var restored=ChatMessageCodec.decode(Map.of("role","assistant","content",stored));
  assertEquals("Tentative advice [W1]",restored.get("content"));assertEquals(web,restored.get("webResearch"));
  assertEquals("Legacy answer",ChatMessageCodec.decode(Map.of("role","assistant","content","Legacy answer")).get("content"));
  assertEquals(stored,ChatMessageCodec.decode(Map.of("role","user","content",stored)).get("content"));
 }

 @Test void tavilyResultsBecomeExcerptsWithSafeSourcesOnly()throws Exception{
  var response=mapper.readTree("""
   {"results":[{"title":"CMPSC 465","url":"https://bulletins.psu.edu/cmpsc/","content":"Prerequisite: CMPSC 360."},
    {"title":"Bad","url":"javascript:alert(1)","content":"x"},{"title":"Reddit","url":"https://www.reddit.com/r/PennStateUniversity/x","content":"Hard but fair."},
    {"title":"Empty","url":"https://example.com","content":""},{"title":"Long","url":"https://example.com/long","content":"%s"}]}""".formatted("y".repeat(900)));
  var result=service.parseTavily(response);
  assertEquals("searched",result.path("status").asText());assertEquals("tavily",result.path("provider").asText());
  assertEquals(3,result.path("sources").size(),"unsafe and empty results are dropped");
  assertEquals("W1",result.at("/sources/0/id").asText());assertEquals("official",result.at("/sources/0/kind").asText());assertEquals("community",result.at("/sources/1/kind").asText());
  assertEquals("W2",result.at("/excerpts/1/sourceIds/0").asText());
  assertTrue(result.at("/excerpts/2/text").asText().length()<=WebResearchService.MAX_EXCERPT+1,"long snippets are cut");
  assertFalse(result.has("searchEntryPoint"));
  assertEquals("no_results",service.parseTavily(mapper.readTree("{\"results\":[]}")).path("status").asText());
 }
 @Test void tavilyQueryIsPublicAndAnchoredToPennState(){
  String q=WebResearchService.tavilyQuery("Is professor Kim good? mail me at a.b@psu.edu id 912345678",List.of());
  assertFalse(q.contains("@"));assertFalse(q.contains("912345678"));assertTrue(q.endsWith("Penn State"));
  assertEquals("CMPSC 465 reviews Penn State",WebResearchService.tavilyQuery("CMPSC 465 reviews",List.of()));
  assertTrue(WebResearchService.tavilyQuery("Is it hard?",List.of(Map.of("role","user","content","Tell me about ASTRO 7N"))).contains("ASTRO 7N"),"course codes carry over from recent messages");
  assertEquals("PSU DS 200 workload",WebResearchService.tavilyQuery("PSU DS 200 workload",List.of()));
 }
 @Test void providerNeedsATavilyKey(){
  assertFalse(new WebResearchService("","test-model","tavily","",mapper).usesTavily(),"no key means Gemini search");
  assertTrue(new WebResearchService("","test-model","tavily","tvly-x",mapper).usesTavily());
  assertFalse(new WebResearchService("","test-model","gemini","tvly-x",mapper).usesTavily());
  assertThrows(IllegalArgumentException.class,()->new WebResearchService("","test-model","bing","",mapper));
 }
}
