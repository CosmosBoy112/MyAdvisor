package dev.lionpathway;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.*;

/** Attaches official catalog facts from the advisor catalog tables to chat, only when the report's major is a loaded program. */
@Service @Profile("live")
public class CatalogDbService {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(CatalogDbService.class);
 // Words that may surround a program name in a report ("Data Sciences (BS)", "... - Univ Park") without changing the program.
 private static final Set<String> NEUTRAL=Set.of("b","s","bs","ba","bachelor","of","arts","science","degree","major","option","in",
  "univ","university","park","up","campus","college","engineering","information","technology","eberly","intercollege");
 private static final Pattern COURSE=Pattern.compile("(?i)\\b([A-Z]{2,6})\\s?(\\d{3}[A-Z]?)\\b");
 private static final Pattern RANGE=Pattern.compile("(20\\d\\d)\\s*[-–—/]\\s*(?:20)?(\\d\\d)");
 private static final Pattern TERM=Pattern.compile("(?i)\\b(spring|summer|fall)\\s+(20\\d\\d)\\b");
 private static final Pattern METADATA=Pattern.compile("(?s)^[^:{]{0,80}metadata \\(not a verbatim quote\\):\\s*(\\{.*)$");
 private static final int MAX_POLICY_CHARS=8000; // whole-catalog snapshots stay out of the chat context
 private final JdbcTemplate jdbc;private final ObjectMapper mapper;
 public CatalogDbService(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}

 record Program(long versionId,String code,String name,String catalogYear,Double minTotalCredits,String sourceUrl,Map<String,Long> options,Map<String,String> optionNames){}
 record Match(Program program,String option,String optionSource){}

 static Set<String> tokens(String text){
  var out=new LinkedHashSet<String>();
  for(String t:text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().split(" "))if(!t.isEmpty())out.add(t.length()>3&&t.endsWith("s")?t.substring(0,t.length()-1):t);
  return out;
 }
 /** A major matches when it contains every word of the program name and nothing else except degree, campus, college or option words. */
 static Optional<Match> matchMajor(String major,List<Program> programs,JsonNode requirements){
  if(major==null||major.isBlank())return Optional.empty();
  var words=tokens(major);
  for(var p:programs){
   var required=tokens(p.name().replaceAll("\\([^)]*\\)",""));required.removeAll(Set.of("b","s","bs","ba"));
   var allowed=new HashSet<>(NEUTRAL);p.optionNames().values().forEach(n->allowed.addAll(tokens(n)));
   var extra=new HashSet<>(words);extra.removeAll(required);
   if(!words.containsAll(required)||!allowed.containsAll(extra))continue;
   for(var o:p.optionNames().keySet())if(words.contains(o.toLowerCase(Locale.ROOT)))return Optional.of(new Match(p,o,"major"));
   // Fall back to an option named in exactly one report requirement title, e.g. "Computational Option List A".
   var found=new LinkedHashSet<String>();
   for(var r:requirements)for(var o:p.optionNames().keySet())if(tokens(r.path("title").asText("")).contains(o.toLowerCase(Locale.ROOT)))found.add(o);
   return Optional.of(found.size()==1?new Match(p,found.iterator().next(),"report_requirements"):new Match(p,null,null));
  }
  return Optional.empty();
 }
 /** "2026-27", "2026-2027" or a Spring/Summer/Fall term → catalog year code; null when the text has no year. */
 static String catalogYear(String text){
  if(text==null)return null;
  var m=RANGE.matcher(text);if(m.find())return m.group(1)+"-"+m.group(2);
  m=TERM.matcher(text);if(!m.find())return null;
  int y=Integer.parseInt(m.group(2));int start=m.group(1).equalsIgnoreCase("spring")?y-1:y; // the edition starts with Summer entry
  return start+"-"+String.format("%02d",(start+1)%100);
 }
 static List<String> courseCodes(String question){
  var out=new LinkedHashSet<String>();var m=COURSE.matcher(question);
  while(m.find()&&out.size()<8)out.add(m.group(1).toUpperCase(Locale.ROOT)+" "+m.group(2).toUpperCase(Locale.ROOT));
  return new ArrayList<>(out);
 }

 /** Never throws: catalog lookup problems must not block the report-only answer. */
 public ObjectNode context(JsonNode report,String question){
  ObjectNode out=mapper.createObjectNode();
  try{
   var programs=programs();
   var match=matchMajor(report.path("major").asText(null),programs,report.path("requirements"));
   if(match.isEmpty()){
    out.put("status","major_not_in_catalog");
    out.put("message","The report's major is not one of the programs loaded in the catalog database. Do not answer catalog questions from it.");
    var names=out.putArray("loadedPrograms");programs.forEach(p->names.add(p.name()));return out;
   }
   var p=match.get().program();String option=match.get().option();
   String reportYear=catalogYear(report.path("catalogYear").asText(null));
   out.put("status",reportYear==null?"matched_year_unknown":reportYear.equals(p.catalogYear())?"matched":"matched_year_differs");
   out.put("program",p.name());out.put("programCode",p.code());out.put("catalogYear",p.catalogYear());
   if(reportYear!=null)out.put("reportCatalogYear",reportYear);
   if(p.minTotalCredits()!=null)out.put("minimumTotalCredits",p.minTotalCredits());
   out.put("bulletinUrl",p.sourceUrl());
   if(option!=null){out.put("option",p.optionNames().get(option));out.put("optionSource",match.get().optionSource());}
   else out.put("option","unknown: requirements for every option are included");
   out.set("requirements",requirements(p,option==null?null:p.options().get(option)));
   jdbc.query("SELECT pv.id, p.name FROM programs p JOIN program_versions pv ON pv.program_code=p.code WHERE p.entrance_for=? AND pv.catalog_year=?",
    rs->{var entrance=out.putObject("entranceToMajor");entrance.put("name",rs.getString(2));
     entrance.set("requirements",requirements(new Program(rs.getLong(1),"","",p.catalogYear(),null,null,Map.of(),Map.of()),null));},p.code(),p.catalogYear());
   out.set("policies",policies(p,option));
   var codes=courseCodes(question);if(!codes.isEmpty())out.set("courses",courses(codes,p.catalogYear()));
   return out;
  }catch(RuntimeException e){
   log.warn("Catalog DB context failed={}",e.getClass().getSimpleName());
   out.removeAll();out.put("status","catalog_unavailable");out.put("message","The catalog database could not be read. Answer from the report only.");return out;
  }
 }

 private List<Program> programs(){
  var rows=jdbc.queryForList("""
   SELECT pv.id, p.code, p.name, pv.catalog_year, pv.min_total_credits, pv.source_url FROM program_versions pv JOIN programs p ON p.code=pv.program_code
   WHERE p.program_type='major' ORDER BY pv.catalog_year DESC""");
  var out=new ArrayList<Program>();
  for(var r:rows){
   long id=((Number)r.get("id")).longValue();var options=new LinkedHashMap<String,Long>();var names=new LinkedHashMap<String,String>();
   jdbc.query("SELECT id, code, name FROM program_options WHERE program_version_id=? ORDER BY id",rs->{options.put(rs.getString(2),rs.getLong(1));names.put(rs.getString(2),rs.getString(3));},id);
   var min=(Number)r.get("min_total_credits");
   out.add(new Program(id,(String)r.get("code"),(String)r.get("name"),(String)r.get("catalog_year"),min==null?null:min.doubleValue(),(String)r.get("source_url"),options,names));
  }
  return out;
 }

 /** Requirement tree for the common requirements plus the given option (all options when null). */
 private ArrayNode requirements(Program p,Long optionId){
  var nodes=new LinkedHashMap<Long,ObjectNode>();var parents=new HashMap<Long,Long>();var optionOf=new HashMap<Long,Long>();
  jdbc.query("""
   SELECT n.id, n.parent_id, n.option_id, o.name AS option_name, n.label, n.node_type, n.min_count, n.min_credits, n.min_grade, n.min_gpa, n.exclusive, n.source_text
   FROM requirement_nodes n LEFT JOIN program_options o ON o.id=n.option_id WHERE n.program_version_id=? ORDER BY n.parent_id NULLS FIRST, n.sort_order, n.id""",rs->{
   ObjectNode n=mapper.createObjectNode();n.put("label",rs.getString("label"));n.put("type",rs.getString("node_type"));
   if(rs.getObject("option_name")!=null&&rs.getObject("parent_id")==null)n.put("option",rs.getString("option_name"));
   for(String f:List.of("min_count","min_credits","min_gpa"))if(rs.getObject(f)!=null)n.put(f,rs.getBigDecimal(f));
   if(rs.getString("min_grade")!=null)n.put("min_grade",rs.getString("min_grade"));
   if(!rs.getBoolean("exclusive"))n.put("may_double_count",true);
   String text=rs.getString("source_text");var meta=text==null?null:METADATA.matcher(text);
   if(meta!=null&&meta.matches()){
    // Loader metadata (review flags, source ids), not Bulletin wording.
    // Keep only review flags and credit ranges; ids, labels and per-row source ids repeat the columns above.
    try{
     JsonNode m=mapper.readTree(meta.group(1));
     if(m.path("requires_review").asBoolean(false))n.put("requires_review",true);
     if(m.has("review"))n.set("review",m.get("review"));
     if(m.has("credits_range"))n.set("credits_range",m.get("credits_range"));
     if(m.has("review")&&m.at("/source/url").isTextual())n.put("source_url",m.at("/source/url").asText());
    }catch(Exception e){n.put("metadata_text",meta.group(1));}
   }else if(text!=null)n.put("bulletin_text",text);
   long id=rs.getLong("id");nodes.put(id,n);
   if(rs.getObject("parent_id")!=null)parents.put(id,rs.getLong("parent_id"));
   if(rs.getObject("option_id")!=null)optionOf.put(id,rs.getLong("option_id"));
  },p.versionId());
  jdbc.query("""
   SELECT r.node_id, r.include, r.course_code, r.subject, r.attribute, r.level_min, r.level_max, v.title, v.credits_min, v.credits_max
   FROM requirement_rules r JOIN requirement_nodes n ON n.id=r.node_id
   LEFT JOIN course_versions v ON v.course_code=r.course_code AND v.catalog_year=? WHERE n.program_version_id=? ORDER BY r.id""",rs->{
   var node=nodes.get(rs.getLong("node_id"));if(node==null)return;
   ObjectNode rule=(node.has("courses")?(ArrayNode)node.get("courses"):node.putArray("courses")).addObject();
   if(!rs.getBoolean("include"))rule.put("exclude",true);
   for(String f:List.of("course_code","subject","attribute","title"))if(rs.getString(f)!=null)rule.put(f.equals("course_code")?"code":f,rs.getString(f));
   for(String f:List.of("level_min","level_max"))if(rs.getObject(f)!=null)rule.put(f,rs.getInt(f));
   if(rs.getObject("credits_min")!=null)rule.put("credits",rs.getBigDecimal("credits_min").equals(rs.getBigDecimal("credits_max"))?rs.getBigDecimal("credits_min").toPlainString():rs.getBigDecimal("credits_min")+"-"+rs.getBigDecimal("credits_max"));
  },p.catalogYear(),p.versionId());
  var roots=mapper.createArrayNode();
  for(var e:nodes.entrySet()){
   var n=e.getValue();
   if(n.path("type").asText().equals("POOL")&&!n.has("courses"))n.put("course_list","not_listed_in_database");
   Long parent=parents.get(e.getKey());
   if(parent==null){if(optionId==null||!optionOf.containsKey(e.getKey())||optionOf.get(e.getKey()).equals(optionId))roots.add(e.getValue());}
   else{var pn=nodes.get(parent);(pn.has("children")?(ArrayNode)pn.get("children"):pn.putArray("children")).add(e.getValue());}
  }
  return roots;
 }

 private ArrayNode policies(Program p,String option){
  var out=mapper.createArrayNode();
  jdbc.query("SELECT key, value::text, description, policy_ref, source_url FROM policy_rules WHERE catalog_year=? AND scope='program' AND scope_code=? ORDER BY key",rs->{
   String key=rs.getString(1);
   if(option!=null&&key.startsWith("suggested_academic_plan.")&&!key.endsWith("."+option))return;
   ObjectNode r=out.addObject();r.put("key",key);
   if(rs.getString(2).length()>MAX_POLICY_CHARS)r.put("value_omitted","too large for chat context; not available to this answer");
   else try{r.set("value",mapper.readTree(rs.getString(2)));}catch(Exception e){throw new IllegalStateException("Unreadable policy value",e);}
   r.put("description",rs.getString(3));if(rs.getString(4)!=null)r.put("reference",rs.getString(4));r.put("source_url",rs.getString(5));
  },p.catalogYear(),p.code());
  return out;
 }

 /** Course facts for codes named in the question: credits, attributes, prerequisite tree and what the course unlocks. */
 private ArrayNode courses(List<String> codes,String year){
  var out=mapper.createArrayNode();
  for(String code:codes){
   var rows=jdbc.queryForList("SELECT id, title, credits_min, credits_max, prereq_text, source_url FROM course_versions WHERE course_code=? AND catalog_year=?",code,year);
   ObjectNode c=out.addObject();c.put("code",code);
   if(rows.isEmpty()){c.put("status","not_in_catalog_database");continue;}
   var row=rows.getFirst();long id=((Number)row.get("id")).longValue();
   c.put("title",(String)row.get("title"));c.put("credits",row.get("credits_min").equals(row.get("credits_max"))?row.get("credits_min").toString():row.get("credits_min")+"-"+row.get("credits_max"));
   if(row.get("prereq_text")!=null)c.put("prerequisite_text",(String)row.get("prereq_text"));
   c.put("source_url",(String)row.get("source_url"));
   var attrs=c.putArray("attributes");jdbc.queryForList("SELECT attribute FROM course_attributes WHERE course_version_id=? ORDER BY 1",String.class,id).forEach(attrs::add);
   var cross=c.putArray("crosslisted_as");jdbc.queryForList("SELECT CASE WHEN course_code=? THEN crosslisted_code ELSE course_code END FROM course_crosslists WHERE ? IN (course_code, crosslisted_code)",String.class,code,code).forEach(cross::add);
   var tree=prereqTree(id);if(tree!=null)c.set("prerequisite_tree",tree);
   var unlocks=c.putArray("unlocks");
   jdbc.queryForList("SELECT DISTINCT v.course_code FROM prereq_nodes p JOIN course_versions v ON v.id=p.course_version_id WHERE p.required_course=? AND v.catalog_year=? ORDER BY 1",String.class,code,year).forEach(unlocks::add);
  }
  return out;
 }
 private JsonNode prereqTree(long versionId){
  var nodes=new LinkedHashMap<Long,ObjectNode>();var parents=new HashMap<Long,Long>();
  jdbc.query("SELECT id, parent_id, node_type, required_course, min_grade, min_semester, raw_text FROM prereq_nodes WHERE course_version_id=? ORDER BY parent_id NULLS FIRST, sort_order, id",rs->{
   ObjectNode n=mapper.createObjectNode();n.put("type",rs.getString("node_type"));
   if(rs.getString("required_course")!=null)n.put("course",rs.getString("required_course"));
   if(rs.getString("min_grade")!=null)n.put("min_grade",rs.getString("min_grade"));
   if(rs.getObject("min_semester")!=null)n.put("min_semester",rs.getInt("min_semester"));
   if(rs.getString("raw_text")!=null)n.put("unparsed_text",rs.getString("raw_text"));
   nodes.put(rs.getLong("id"),n);if(rs.getObject("parent_id")!=null)parents.put(rs.getLong("id"),rs.getLong("parent_id"));
  },versionId);
  JsonNode root=null;
  for(var e:nodes.entrySet()){
   Long parent=parents.get(e.getKey());
   if(parent==null)root=e.getValue();else{var pn=nodes.get(parent);(pn.has("children")?(ArrayNode)pn.get("children"):pn.putArray("children")).add(e.getValue());}
  }
  return root;
 }
}
