package dev.lionpathway;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.util.*;

/** Deterministic, version-scoped reference matching. This is not a graduation audit. */
@Service
public class CatalogService {
 private final ObjectMapper mapper;
 private final JsonNode program, crosswalk;
 private final Map<String,JsonNode> requirements = new HashMap<>(), courses = new HashMap<>();
 private final String bulletin;
 public CatalogService(ObjectMapper mapper) throws IOException {
  this.mapper=mapper;
  program=read("programs/CMPSC_BS.json");crosswalk=read("references/CMPSC_handbook_crosswalk.json");
  index(program.path("requirements"));
  for(var c:read("courses.json").path("courses"))courses.put(c.path("code").asText(),c);
  bulletin=program.path("source_urls").path(0).asText();
 }
 private JsonNode read(String path) throws IOException {
  try(var in=new ClassPathResource("catalog/2026-27/"+path).getInputStream()){return mapper.readTree(in);}
 }
 private void index(JsonNode nodes){for(var n:nodes){if(n.has("id"))requirements.put(n.path("id").asText(),n);index(n.path("children"));}}
 private static String normalized(String text){return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim();}
 private static boolean year(String text){return text!=null&&text.matches(".*(?<![0-9])2026\\s*[-–—/]\\s*(?:2027|27)(?![0-9]).*");}
 private static boolean major(String text){return Set.of("computer science","computer science bs","computer science b s","computer science b s engineering","cmpsc bs").contains(normalized(text));}
 private static boolean campus(String text){return Set.of("up","university park").contains(normalized(text));}

 /** New uploads must have report metadata corroborated by PDF text; user intent is never scope evidence. */
 public ObjectNode enrich(ObjectNode report, Map<Integer,String> pages) {
  report.remove("catalog");
  for(var item:report.path("requirements"))if(item instanceof ObjectNode node)node.remove("catalogMatch");
  ObjectNode catalog=report.putObject("catalog");
  catalog.put("version","CMPSC_BS:UP:2026-27:v1");catalog.put("bulletinUrl",bulletin);
  catalog.put("target","Computer Science B.S. · University Park · 2026–27");
  catalog.put("handbookYear",crosswalk.path("handbook_year").asText());
  catalog.put("handbookUrl",crosswalk.path("handbook_url").asText());
  catalog.put("automaticAudit",false);
  if(!major(report.path("major").asText(""))||!campus(report.path("campus").asText(""))||!year(report.path("catalogYear").asText(""))){
   catalog.put("status","scope_not_matched");catalog.put("message","This catalog covers only Computer Science B.S., University Park, 2026–27. The report's major, campus or requirement year is missing or different. No catalog rules were applied.");return report;
  }
  String text=String.join("\n",pages.values());String plain=normalized(text);
  // This corroborates extracted metadata, not the academic correctness of the report.
  boolean supported=(plain.contains("computer science")||plain.contains("cmpsc bs")) && plain.contains("university park") && year(text.replace('\n',' '));
  if(!supported){catalog.put("status","scope_unverified");catalog.put("message","Report scope could not be corroborated in the extracted PDF text. Upload a report showing the major, University Park campus and 2026–27 requirement year to compare official requirements.");return report;}
  catalog.put("status","matched_with_review");catalog.put("scopeEvidence","report_text");
  catalog.put("minimumTotalCredits",program.path("min_total_credits").asInt());
  catalog.put("message","Compared with the 2026–27 Bulletin. The linked 2024–25 Handbook is historical only. Matching labels does not verify course eligibility or graduation; PDF credits and completion remain unchanged.");
  if(report.path("totals").path("required").isNumber() && report.path("totals").path("required").asDouble()!=program.path("min_total_credits").asDouble())catalog.put("totalCreditConflict",true);
  int count=0;
  for(var item:report.path("requirements"))if(item instanceof ObjectNode node){
   ObjectNode match=match(node);node.set("catalogMatch",match);
   if(!match.path("status").asText().equals("unmatched"))count++;
  }
  catalog.put("matchedRequirements",count);return report;
 }
 /** Old saved reports lack PDF scope evidence. Do not infer it from user goals or silently migrate them. */
 public JsonNode forRead(JsonNode stored){
  ObjectNode copy=stored.deepCopy();
  if(!copy.has("catalog"))enrich(copy,Map.of());
  return RequirementPlanning.annotate(copy);
 }
 private ObjectNode match(ObjectNode item){
  ObjectNode out=mapper.createObjectNode();out.put("requiresReview",true);out.put("automaticApplicationAllowed",false);
  String title=normalized(item.path("title").asText());
  List<JsonNode> candidates=new ArrayList<>();
  for(var mapping:crosswalk.path("mappings"))for(var alias:mapping.path("report_aliases"))if(normalized(alias.asText()).equals(title)){candidates.add(mapping);break;}
  if(candidates.size()!=1){out.put("status","unmatched");out.put("message","No exact catalog label match. Confirm this report section with an adviser.");return out;}
  JsonNode mapping=candidates.getFirst();
  out.put("status",mapping.path("match_status").asText());out.put("message",mapping.path("summary").asText());
  out.put("handbookYear",crosswalk.path("handbook_year").asText());out.set("handbookPages",mapping.path("handbook_pages").deepCopy());
  JsonNode rule=requirements.get(mapping.path("bulletin_requirement_id").asText());
  if(rule!=null){
   out.put("requirementId",rule.path("id").asText());out.put("bulletinUrl",rule.path("source").path("url").asText(bulletin));
   out.set("rule",rule.deepCopy()); // Preserve AND/OR and unresolved branches for both chat providers.
   if(rule.has("min_credits")){
    out.set("requiredCredits",rule.path("min_credits"));
    if(item.path("required").isNumber())out.put("creditConflict",item.path("required").asDouble()!=rule.path("min_credits").asDouble());
   }
   ArrayNode options=out.putArray("courseOptions");addCourses(rule,options,new HashSet<>());
  }
  return out;
 }
 private void addCourses(JsonNode rule,ArrayNode out,Set<String> seen){
  for(var r:rule.path("rules")){
   String code=r.path("course").asText("");if(code.isEmpty()||!seen.add(code))continue;
   ObjectNode option=out.addObject();option.put("code",code);
   JsonNode c=courses.get(code);if(c!=null){
    option.set("title",c.path("title"));option.set("sourceUrl",c.path("source_url"));
    option.set("prerequisiteText",c.path("prereq_text"));option.set("review",c.path("review"));
   }
  }
  for(var child:rule.path("children"))addCourses(child,out,seen);
 }
}
