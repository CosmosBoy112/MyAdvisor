package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
/**
 * Tools the advisor agent may call (docs/advisor-tools.md, v1 lineup). Every result uses the envelope
 * {ok, data, sources, warnings} or {ok:false, error:{code, message}}. The student is never an argument:
 * student tools read only the report of the current request.
 */
@Service @Profile("live")
public class AdvisorTools {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(AdvisorTools.class);
 static final int MAX_LIST=20;
 private static final Pattern CODE=Pattern.compile("(?i)^\\s*([A-Z]{2,6})\\s*(\\d{1,3}[A-Z]{0,2})\\s*$"); // CMPSC 465, ENGL 15, ASTRO 7N, CAS 100A
 static final Set<String> VIEWS=Set.of("course_detail","course_options","requirement_gap","semester_plan","minor_check");
 static final Set<String> MEMORY_CATEGORIES=Set.of("preference","goal","constraint","other");
 private final CatalogDbService catalog;private final MathMinorService minor;private final WebResearchService web;private final AdvisorStore store;private final ObjectMapper mapper;
 public AdvisorTools(CatalogDbService catalog,MathMinorService minor,WebResearchService web,ObjectMapper mapper){this(catalog,minor,web,null,mapper);}
 @org.springframework.beans.factory.annotation.Autowired
 public AdvisorTools(CatalogDbService catalog,MathMinorService minor,WebResearchService web,AdvisorStore store,ObjectMapper mapper){this.catalog=catalog;this.minor=minor;this.web=web;this.store=store;this.mapper=mapper;}

 /** Per-request state: the student's report, the catalog scope resolved once, and the web evidence shown with the answer. */
 static final class Turn {
  final JsonNode report;final List<Map<String,Object>> history;final Optional<CatalogDbService.Scope> scope;final boolean webEnabled;
  ObjectNode webResearch;
  // Set by memory-enabled turns: whose memories the memory tools touch, and which report the answer belongs to.
  String subject;java.util.UUID reportId;List<Map<String,Object>> memories=List.of();
  /** The screen the model asked for (show_view); its data is filled by the server in AdvisorViews. */
  ObjectNode viewRequest;
  /** "campus|program": public words added to web searches; never grades or records. */
  String searchContext="";
  final List<Map<String,Object>> memoryChanges=new ArrayList<>();
  Turn(JsonNode report,List<Map<String,Object>> history,Optional<CatalogDbService.Scope> scope,boolean webEnabled){this.report=report;this.history=history;this.scope=scope;this.webEnabled=webEnabled;}
 }
 Turn turn(JsonNode report,List<Map<String,Object>> history){return turn(report,history,null,null);}
 Turn turn(JsonNode report,List<Map<String,Object>> history,String subject,java.util.UUID reportId){
  Optional<CatalogDbService.Scope> scope;
  try{scope=catalog.scope(report);}catch(RuntimeException e){log.warn("Advisor scope lookup failed={}",e.getClass().getSimpleName());scope=Optional.empty();}
  var turn=new Turn(report,history,scope,report.path("webSearchEnabled").asBoolean(true)&&web!=null);
  turn.searchContext=searchContext(report,scope);
  if(store!=null&&subject!=null){turn.subject=subject;turn.reportId=reportId;try{turn.memories=store.memories(subject);}catch(RuntimeException e){log.warn("Memory lookup failed={}",e.getClass().getSimpleName());}}
  return turn;
 }
 static final String SCHOOL="Penn State University";
 private static final Set<String> NOT_EARNED=Set.of("F","W","UN","AU","LD","NG","XF");
 static String searchContext(JsonNode report,Optional<CatalogDbService.Scope> scope){
  String campus=report.path("campus").asText("").replace("|"," ").strip();
  String program=scope.map(s->s.program().name()).orElse(report.path("major").asText("")).replace("|"," ").replaceAll("\\(.*?\\)|,?\\s*B\\.\\s?[SA]\\.?","").replaceAll("\\s+"," ").strip();
  return (campus.length()<=60?campus:"")+"|"+(program.length()<=60?program:"");
 }
 /**
  * The always-present picture of the student, so vague questions can be read in context: school, program, every course
  * row from the report (best row per course) and the requirements still open. Built from the report only.
  */
 static ObjectNode profile(JsonNode report,Optional<CatalogDbService.Scope> scope,List<Map<String,Object>> history,String question,ObjectMapper mapper){
  var p=mapper.createObjectNode();p.put("school",SCHOOL);
  for(String f:List.of("campus","major","minor","catalogYear","intent"))if(report.hasNonNull(f))p.set(f,report.get(f));
  scope.ifPresent(s->{p.put("program",s.program().name());p.put("programCode",s.program().code());if(s.option()!=null)p.put("option",s.program().optionNames().get(s.option()));});
  var best=new LinkedHashMap<String,JsonNode>();
  for(var c:report.path("courseInventory").path("courses")){
   String code=c.path("code").asText();var old=best.get(code);
   if(old==null||rank(c)<rank(old))best.put(code,c);
  }
  var courses=p.putObject("courses");var done=courses.putArray("completed");var ip=courses.putArray("inProgress");var failed=courses.putArray("notEarned");var review=courses.putArray("needsReview");
  for(var e:best.entrySet()){
   var c=e.getValue();String grade=c.path("grade").asText("");int r=rank(c);
   var target=r==0?done:r==1?ip:r==2?review:failed;if(target.size()>=80)continue;
   target.add(r==1?e.getKey():e.getKey()+(grade.isEmpty()?"":" ("+grade+")"));
  }
  if(best.isEmpty())courses.put("note","No course rows were recognized in this report.");
  var open=p.putArray("openRequirements");var covered=p.putArray("coveredByCurrentCourses");int complete=0;
  for(var r:report.path("requirements")){
   if(r.path("coveredByPlan").asBoolean(false)){covered.add(r.path("title").asText());continue;}
   if(r.path("status").asText().equals("completed")){complete++;continue;}
   var o=open.addObject();for(String f:List.of("title","status","required","completed","inProgress"))if(r.has(f))o.set(f,r.get(f));
  }
  p.put("completedRequirementCount",complete);
  // The course the student most recently talked about, for "is it hard?" / "that one".
  var recent=new LinkedHashSet<String>();var code=java.util.regex.Pattern.compile("\\b[A-Z]{2,6}\\s?\\d{1,3}[A-Z]{0,2}\\b");
  var texts=new ArrayList<String>();
  for(var m:history.subList(Math.max(0,history.size()-4),history.size())){
   var text=new StringBuilder(String.valueOf(m.getOrDefault("content","")));
   // An answer that opened a screen may name its courses only there.
   if(m.get("view") instanceof JsonNode v){for(var c:v.at("/data/courses"))text.append(' ').append(c.path("code").asText());}
   texts.add(text.toString());
  }
  texts.add(question);
  for(int i=texts.size()-1;i>=0&&recent.size()<5;i--){var m=code.matcher(texts.get(i));while(m.find()&&recent.size()<5)recent.add(m.group().replaceAll("([A-Z])(\\d)","$1 $2"));}
  p.set("recentlyDiscussedCourses",mapper.valueToTree(recent));
  return p;
 }
 // 0 completed, 1 in progress, 2 conflicting rows, 3 not earned (F, W, ...): lower is the row that counts.
 private static int rank(JsonNode c){
  if(c.path("requiresReview").asBoolean(false))return 2;String g=c.path("grade").asText("").toUpperCase(Locale.ROOT);
  return g.equals("IP")?1:NOT_EARNED.contains(g)?3:0;
 }
 boolean memoryEnabled(Turn turn){return store!=null&&turn.subject!=null;}

 ArrayNode declarations(Turn turn){
  var tools=mapper.createArrayNode();
  tools.add(fn("get_student_record","The student's own degree report: summary, requirement statuses with PDF evidence, or course rows (completed and in progress). Read this before judging what the student has taken.",
   props("section",enumOf("summary|requirements|courses","Which part of the report"),"requirement",str("Optional case-insensitive filter on requirement titles"),"status",enumOf("recorded|in_progress","Optional course-row filter")),"section"));
  tools.add(fn("search_courses","Find catalog courses by English keywords and filters. Use when the student describes a course without its code (e.g. 'algorithms', 'machine learning'). At least one argument is required.",
   props("query",str("English keywords; every word must appear in the title, description or code"),"subject",str("Subject code, e.g. 'CMPSC'"),"level_min",integer("e.g. 400"),"level_max",integer("e.g. 499"),
    "attributes",strArray("All must match, e.g. ['GQ']"),"limit",integer("Default 10, max 20"))));
  tools.add(fn("get_course","Official catalog facts for one course: title, credits, description, attributes, cross-listings, prerequisite tree and wording, and which courses it unlocks.",
   props("code",str("Course code, e.g. 'CMPSC 465'")),"code"));
  tools.add(fn("check_prerequisites","Deterministically checks prerequisite trees against the course rows in the student's report. Use this instead of judging prerequisites yourself. Pass every course you are considering in one call with codes.",
   props("code",str("One course code, e.g. 'CMPSC 465'"),"codes",strArray("Up to 8 course codes checked in one call, e.g. ['STAT 414','CMPSC 465']"))));
  tools.add(fn("get_program_requirements","Official requirement tree of a loaded program, with node ids. Without node_id it is an overview (course_count per node); pass node_id for one subtree with its courses. Defaults to the report's major and option.",
   props("program_code",str("e.g. 'DATSC_BS', 'CMPSC_BS', 'CMPSC_ETM'. Defaults to the report's major"),"option",str("Option code, e.g. 'COMPUTATIONAL'"),"node_id",integer("Return only this node and its children"))));
  tools.add(fn("find_courses_for_requirement","Courses that can satisfy one requirement node, marked with whether the student's report already has them.",
   props("node_id",integer("Requirement node id from get_program_requirements"),"limit",integer("Default 20, max 20")),"node_id"));
  tools.add(fn("get_policies","Program policies and the suggested semester plan from the Bulletin/Handbook, with their source edition.",
   props("program_code",str("Defaults to the report's major"),"key",str("Optional policy key, e.g. 'suggested_academic_plan.COMPUTATIONAL'"))));
  if(minor!=null)tools.add(fn("get_minor_assessment","Server-computed reference comparison of the student's courses with the Mathematics minor (2026-27 University Park). Only MATH is supported.",
   props("minor",enumOf("MATH","Minor code")),"minor"));
  if(turn.webEnabled)tools.add(fn("web_search","Searches the public web (Penn State official sources first; Reddit/Rate My Professors for experiences). Use for professors, workload, offerings, or programs not in the catalog database. Call at most once per answer. Never put the student's grades, records or personal details in the query.",
   props("query",str("A public question in plain words"),"sources",enumOf("official|community|any","official = Penn State sites only (requirements, policies, offerings); community = Reddit and Rate My Professors only (experiences, workload, professors); any = whole web (default)")),"query"));
  tools.add(fn("show_view","Opens one screen next to your answer. Call it once, after the tools that support it, when a screen helps: course_detail (one to three courses the student asks about), course_options (courses you recommend), requirement_gap (what is still missing), semester_plan (only when the student asks for planning), minor_check (Math minor). The server fills the screen from the catalog and report; you only choose it.",
   props("view",enumOf(String.join("|",List.of("course_detail","course_options","requirement_gap","semester_plan","minor_check")),"Screen type"),"course_codes",strArray("Courses for course_detail (max 3) or course_options (max 10)"),"node_ids",integerArray("Requirement node ids for requirement_gap (max 3)")),"view"));
  if(memoryEnabled(turn)){
   tools.add(fn("save_memory","Saves one fact or preference the student stated themselves (e.g. 'wants to avoid 8 am classes', 'aiming for data engineering'). Never save your guesses, grades, health or other sensitive details. The student sees a notice and can undo it.",
    props("category",enumOf("preference|goal|constraint|other","Kind of memory"),"content",str("One sentence, at most 500 characters, in the student's language"),"replaces_id",integer("Existing memory id to update instead of adding")),"category","content"));
   tools.add(fn("forget_memory","Deletes a saved memory when the student asks to forget it or it conflicts with what they just said.",props("id",integer("Memory id from STUDENT CONTEXT memories")),"id"));
  }
  return tools;
 }

 ObjectNode call(String name,JsonNode args,Turn turn){
  long started=System.nanoTime();ObjectNode result;
  try{
   result=switch(name){
    case "get_student_record"->studentRecord(args,turn);
    case "search_courses"->searchCourses(args,turn);
    case "get_course"->getCourse(args,turn);
    case "check_prerequisites"->checkPrerequisites(args,turn);
    case "get_program_requirements"->programRequirements(args,turn);
    case "find_courses_for_requirement"->coursesForRequirement(args,turn);
    case "get_policies"->policies(args,turn);
    case "get_minor_assessment"->minorAssessment(args,turn);
    case "web_search"->webSearch(args,turn);
    case "show_view"->showView(args,turn);
    case "save_memory"->saveMemory(args,turn);
    case "forget_memory"->forgetMemory(args,turn);
    default->error("INVALID_ARGUMENT","Unknown tool: "+name);
   };
  }catch(IllegalArgumentException e){result=error("INVALID_ARGUMENT",e.getMessage());}
  catch(RuntimeException e){log.warn("Advisor tool {} failed={}",name,e.getClass().getSimpleName());result=error("DATA_NOT_AVAILABLE","The catalog database could not be read. Answer from the report and say this was not verified.");}
  log.info("Advisor tool {} ok={} elapsedMs={}",name,result.path("ok").asBoolean(),(System.nanoTime()-started)/1_000_000);
  return result;
 }

 private ObjectNode studentRecord(JsonNode args,Turn turn){
  var r=turn.report;var data=mapper.createObjectNode();String section=args.path("section").asText("summary");
  switch(section){
   case "summary"->{
    for(String f:List.of("major","minor","campus","catalogYear","totals","warnings","intent"))if(r.has(f))data.set(f,r.get(f));
    var counts=data.putObject("requirementStatusCounts");for(var q:r.path("requirements"))counts.put(q.path("status").asText("unknown"),counts.path(q.path("status").asText("unknown")).asInt()+1);
    data.put("courseRowsInReport",r.path("courseInventory").path("courseCount").asInt(0));
   }
   case "requirements"->{
    String filter=args.path("requirement").asText("").toLowerCase(Locale.ROOT);var list=data.putArray("requirements");
    for(var q:r.path("requirements"))if(filter.isEmpty()||q.path("title").asText().toLowerCase(Locale.ROOT).contains(filter))list.add(q);
    if(r.has("catalog"))data.set("catalogComparison",r.path("catalog").path("status"));
   }
   case "courses"->{
    var inv=r.path("courseInventory");
    if(!inv.path("courses").isArray())return error("DATA_NOT_AVAILABLE","This report has no recognizable course rows. Ask the student for a report that lists course history.");
    String status=args.path("status").asText(null);var list=data.putArray("courses");
    for(var c:inv.path("courses"))if(status==null||status.equals(c.path("status").asText()))list.add(c);
    data.put("coverage","Course rows found in this PDF only; not a verified complete transcript.");data.put("unparsedRowCount",inv.path("unparsedRowCount").asInt(0));
   }
   default->throw new IllegalArgumentException("section must be summary, requirements or courses");
  }
  return ok(data,mapper.createArrayNode().add(source("record","uploaded degree report",null)),List.of());
 }

 private ObjectNode searchCourses(JsonNode args,Turn turn){
  String query=text(args,"query"),subject=text(args,"subject");Integer min=integerArg(args,"level_min"),max=integerArg(args,"level_max");var attrs=new ArrayList<String>();args.path("attributes").forEach(a->attrs.add(a.asText()));
  if(query==null&&subject==null&&min==null&&max==null&&attrs.isEmpty())throw new IllegalArgumentException("Give at least one of query, subject, level_min, level_max or attributes.");
  String year=year(turn);var found=catalog.searchCourses(query,subject,min,max,attrs,year,limit(args,10));
  var data=mapper.createObjectNode();data.put("catalogYear",year);data.set("courses",found);
  var warnings=new ArrayList<>(yearWarnings(turn));if(found.isEmpty())warnings.add("No loaded "+year+" course matched. The database holds only courses referenced by the loaded programs; try other English keywords or web_search.");
  return ok(data,mapper.createArrayNode().add(source("catalog","course_versions@"+year,"https://bulletins.psu.edu/university-course-descriptions/undergraduate/")),warnings);
 }

 private ObjectNode getCourse(JsonNode args,Turn turn){
  String code=code(args),year=year(turn);var course=(ObjectNode)catalog.courses(List.of(code),year).get(0);
  if(course.has("status"))return error("NOT_FOUND",code+" is not in the "+year+" catalog database. Use search_courses or web_search.");
  catalog.description(code,year).ifPresent(d->course.put("description",d.length()>1500?d.substring(0,1500)+"...":d));
  if(course.path("prerequisite_tree").toString().contains("TEXT"))course.put("note","TEXT prerequisite nodes hold Bulletin wording the parser could not structure; quote unparsed_text.");
  return ok(course,mapper.createArrayNode().add(source("catalog","course_versions:"+code+"@"+year,course.path("source_url").asText(null))),yearWarnings(turn));
 }

 static final int MAX_BATCH=8;
 /** One code, or a batch under "codes" (one tool call): {results:[per-course envelope data or error]}. */
 private ObjectNode checkPrerequisites(JsonNode args,Turn turn){
  if(!args.path("codes").isArray())return checkPrerequisite(args,turn);
  var results=mapper.createArrayNode();var warnings=new LinkedHashSet<String>();var sources=mapper.createArrayNode();
  for(var c:args.path("codes")){
   if(results.size()>=MAX_BATCH)break;
   ObjectNode one;try{one=checkPrerequisite(mapper.createObjectNode().put("code",c.asText()),turn);}catch(IllegalArgumentException e){one=error("INVALID_ARGUMENT",c.asText()+": "+e.getMessage());}
   if(one.path("ok").asBoolean()){results.add(one.path("data"));one.path("warnings").forEach(w->warnings.add(w.asText()));sources.add(one.at("/sources/0"));}
   else results.addObject().put("course",c.asText()).set("error",one.path("error"));
  }
  if(results.isEmpty())throw new IllegalArgumentException("codes must list at least one course code");
  if(args.path("codes").size()>MAX_BATCH)warnings.add("Only the first "+MAX_BATCH+" courses were checked.");
  return ok(mapper.createObjectNode().set("results",results),sources,new ArrayList<>(warnings));
 }
 private ObjectNode checkPrerequisite(JsonNode args,Turn turn){
  String code=code(args),year=year(turn);var basics=catalog.courseBasics(code,year);
  if(basics.isEmpty())return error("NOT_FOUND",code+" is not in the "+year+" catalog database, so its prerequisites cannot be checked.");
  var id=Optional.of(((Number)basics.get().get("id")).longValue());
  var courses=turn.report.path("courseInventory").path("courses");
  if(!courses.isArray())return error("DATA_NOT_AVAILABLE","This report has no recognizable course rows, so prerequisites cannot be checked. Ask for a report that lists course history.");
  var tree=catalog.prereqTree(id.get());var required=new LinkedHashSet<String>();collectCourses(tree,required);
  var data=PrerequisiteChecker.evaluate(tree,courses,catalog.crosslists(required),mapper);data.put("course",code);data.put("catalogYear",year);
  var info=mapper.createObjectNode();if(basics.get().get("prereq_text")!=null)info.put("prerequisite_text",(String)basics.get().get("prereq_text"));if(basics.get().get("source_url")!=null)info.put("source_url",(String)basics.get().get("source_url"));
  if(info.has("prerequisite_text"))data.put("prerequisite_text",info.path("prerequisite_text").asText());
  for(var c:courses)if(c.path("code").asText().equals(code)){data.set("alreadyInReport",c);break;}
  var warnings=new ArrayList<>(yearWarnings(turn));
  warnings.add("Checked against course rows found in this PDF only. Enrollment restrictions (major, campus, semester standing) are not in the database and were not checked.");
  return ok(data,mapper.createArrayNode().add(source("catalog","prereq_nodes:"+code+"@"+year,info.path("source_url").asText(null))).add(source("record","uploaded degree report",null)),warnings);
 }

 private ObjectNode programRequirements(JsonNode args,Turn turn){
  var p=program(args,turn);if(p.isEmpty())return programMissing(args,turn);
  var program=p.get();String option=option(args,turn,program);Long node=integerArg(args,"node_id")==null?null:args.path("node_id").asLong();
  var data=mapper.createObjectNode();data.put("program",program.name());data.put("programCode",program.code());data.put("catalogYear",program.catalogYear());
  if(program.minTotalCredits()!=null)data.put("minimumTotalCredits",program.minTotalCredits());
  data.put("option",option==null?"unknown: requirements for every option are included":program.optionNames().get(option));
  var tree=catalog.requirements(program,option==null?null:program.options().get(option),true,node);
  if(node!=null&&tree.isEmpty())return error("NOT_FOUND","Node "+node+" is not part of "+program.code()+".");
  // The whole tree with every course rule is large and slows the model; an overview keeps counts and node ids to drill into.
  if(node==null)summarize(tree);
  data.set("requirements",tree);
  var warnings=new ArrayList<>(yearWarnings(turn));
  if(node==null)warnings.add("Overview: course lists are replaced by course_count. Pass node_id, or call find_courses_for_requirement, for a node's courses.");
  warnings.add("POOL nodes with course_list not_listed_in_database have an unknown approved list; requires_review flags must stay explicit.");
  return ok(data,mapper.createArrayNode().add(source("catalog","program_versions:"+program.code()+"@"+program.catalogYear(),program.sourceUrl())),warnings);
 }

 private ObjectNode coursesForRequirement(JsonNode args,Turn turn){
  if(integerArg(args,"node_id")==null)throw new IllegalArgumentException("node_id is required");
  long node=args.path("node_id").asLong();String year=year(turn);
  var found=catalog.requirementCourses(node,year,limit(args,MAX_LIST));if(found.isEmpty())return error("NOT_FOUND","Unknown requirement node "+node+". Get ids from get_program_requirements.");
  var data=found.get();var taken=new HashMap<String,JsonNode>();
  for(var c:turn.report.path("courseInventory").path("courses"))taken.merge(c.path("code").asText(),c,(a,b)->a.path("grade").asText().equals("IP")?b:a);
  for(var c:data.path("courses")){var row=taken.get(c.path("code").asText());((ObjectNode)c).put("in_report",row==null?"no":row.path("grade").asText().equals("IP")?"in_progress":"recorded:"+row.path("grade").asText());}
  var warnings=new ArrayList<>(yearWarnings(turn));
  warnings.add("A listed course is an option for this requirement, not verified eligibility or completion. Check prerequisites separately.");
  if(data.path("truncated").asBoolean())warnings.add("More courses match than were returned.");
  return ok(data,mapper.createArrayNode().add(source("catalog","requirement_rules:"+node+"@"+year,null)),warnings);
 }

 private ObjectNode policies(JsonNode args,Turn turn){
  var p=program(args,turn);if(p.isEmpty())return programMissing(args,turn);
  var program=p.get();String key=text(args,"key");var all=catalog.policies(program,option(args,turn,program));var list=mapper.createArrayNode();
  for(var r:all)if(key==null||key.equals(r.path("key").asText()))list.add(r);
  if(key!=null&&list.isEmpty()){var keys=new ArrayList<String>();all.forEach(r->keys.add(r.path("key").asText()));return error("NOT_FOUND","No policy "+key+" for "+program.code()+". Available keys: "+String.join(", ",keys));}
  var data=mapper.createObjectNode();data.put("programCode",program.code());data.put("catalogYear",program.catalogYear());data.set("policies",list);
  var sources=mapper.createArrayNode();for(var r:list)sources.add(source("policy_rule",r.path("key").asText(),r.path("source_url").asText(null)));
  var warnings=new ArrayList<>(yearWarnings(turn));warnings.add("Policies whose description names an earlier Handbook edition must be presented with that edition and confirmed with an adviser.");
  return ok(data,sources,warnings);
 }

 private ObjectNode minorAssessment(JsonNode args,Turn turn){
  if(!"MATH".equals(args.path("minor").asText()))return error("DATA_NOT_AVAILABLE","Only the Mathematics minor has a server comparison. Use web_search for other minors.");
  var result=turn.report.has("minorAssessment")?turn.report.get("minorAssessment"):minor.evaluate(turn.report);
  return ok(result,mapper.createArrayNode().add(source("audit","MATH_UMNR@2026-27",result.path("sourceUrl").asText(null))),
   List.of("Reference estimate only: never state the minor is satisfied or guaranteed. Keep catalog-year, campus and 6-credit separation checks explicit."));
 }

 private ObjectNode webSearch(JsonNode args,Turn turn){
  if(!turn.webEnabled)return error("DATA_NOT_AVAILABLE","Web search is turned off for this question.");
  if(turn.webResearch!=null)return error("RATE_LIMITED","Web search already ran for this answer; use those results.");
  String query=text(args,"query");if(query==null)throw new IllegalArgumentException("query is required");
  var research=web.search(query,turn.history,args.path("sources").asText("any"),turn.searchContext);turn.webResearch=research;
  var forModel=research.deepCopy();forModel.remove("searchEntryPoint");
  String status=research.path("status").asText();
  if(!status.equals("searched"))return error("DATA_NOT_AVAILABLE","Web search status: "+status+". Say web verification did not succeed; do not invent findings.");
  return ok(forModel,mapper.createArrayNode().add(source("web","webResearch",null)),
   List.of("Cite each external claim with its sourceIds, e.g. [W1]. Reddit/Rate My Professors are subjective and never prove a requirement."));
 }

 private ObjectNode showView(JsonNode args,Turn turn){
  String view=args.path("view").asText();if(!VIEWS.contains(view))throw new IllegalArgumentException("view must be one of "+VIEWS);
  if(turn.viewRequest!=null)return error("RATE_LIMITED","A screen is already open for this answer.");
  var request=mapper.createObjectNode();request.put("view",view);
  var codes=request.putArray("course_codes");for(var c:args.path("course_codes"))if(codes.size()<(view.equals("course_options")?10:3))codes.add(code(mapper.createObjectNode().put("code",c.asText())));
  var nodes=request.putArray("node_ids");for(var n:args.path("node_ids"))if(n.isNumber()&&nodes.size()<3)nodes.add(n.asLong());
  if(Set.of("course_detail","course_options").contains(view)&&codes.isEmpty())throw new IllegalArgumentException(view+" needs course_codes");
  turn.viewRequest=request;
  return ok(mapper.createObjectNode().put("opened",view),mapper.createArrayNode(),List.of("The screen shows server-verified data; do not repeat every number in your text."));
 }
 private ObjectNode saveMemory(JsonNode args,Turn turn){
  if(!memoryEnabled(turn))return error("DATA_NOT_AVAILABLE","Memory is not available for this conversation.");
  String category=args.path("category").asText(),content=text(args,"content");
  if(!MEMORY_CATEGORIES.contains(category))throw new IllegalArgumentException("category must be one of "+MEMORY_CATEGORIES);
  if(content==null||content.length()>500)throw new IllegalArgumentException("content must be one sentence of at most 500 characters");
  var saved=store.saveMemory(turn.subject,turn.reportId,category,content,integerArg(args,"replaces_id")==null?null:args.path("replaces_id").asLong());
  var change=new LinkedHashMap<String,Object>(saved);change.put("action","saved");turn.memoryChanges.add(change);
  return ok(mapper.valueToTree(saved),mapper.createArrayNode(),List.of("The student is shown this memory and can undo it."));
 }
 private ObjectNode forgetMemory(JsonNode args,Turn turn){
  if(!memoryEnabled(turn))return error("DATA_NOT_AVAILABLE","Memory is not available for this conversation.");
  if(integerArg(args,"id")==null)throw new IllegalArgumentException("id is required");long id=args.path("id").asLong();
  if(!store.forgetMemory(turn.subject,id))return error("NOT_FOUND","Memory "+id+" does not exist.");
  turn.memoryChanges.add(Map.of("id",id,"action","forgotten"));
  return ok(mapper.createObjectNode().put("forgotten",id),mapper.createArrayNode(),List.of());
 }

 private Optional<CatalogDbService.Program> program(JsonNode args,Turn turn){
  String code=text(args,"program_code");
  if(code==null)return turn.scope.map(CatalogDbService.Scope::program);
  return catalog.program(code.toUpperCase(Locale.ROOT));
 }
 private ObjectNode programMissing(JsonNode args,Turn turn){
  String code=text(args,"program_code");
  return error(code==null?"PROFILE_INCOMPLETE":"NOT_FOUND",(code==null?"The report's major is not a loaded program.":"Program "+code+" is not loaded.")+" Loaded majors: "+String.join("; ",catalog.loadedProgramNames())+". Use web_search for other programs.");
 }
 /** The option given (when the program has it), else the report's option when asking about the report's own program. */
 private String option(JsonNode args,Turn turn,CatalogDbService.Program program){
  String option=text(args,"option");
  if(option!=null){option=option.toUpperCase(Locale.ROOT);if(!program.options().containsKey(option))throw new IllegalArgumentException("Unknown option "+option+" for "+program.code()+". Options: "+program.options().keySet());return option;}
  return turn.scope.filter(s->s.program().code().equals(program.code())).map(CatalogDbService.Scope::option).orElse(null);
 }
 private String year(Turn turn){return turn.scope.map(s->s.program().catalogYear()).orElseGet(catalog::defaultYear);}
 private List<String> yearWarnings(Turn turn){
  if(turn.scope.isEmpty())return List.of("The report's major is not a loaded program; these are general catalog facts, not the student's requirements.");
  var s=turn.scope.get();
  if(s.status().equals("matched_year_differs"))return List.of("The database holds the "+s.program().catalogYear()+" edition while the report follows "+s.reportYear()+"; requirements may differ. Suggest the archived Bulletin for their year.");
  if(s.status().equals("matched_year_unknown"))return List.of("The report's requirement year could not be confirmed.");
  return List.of();
 }
 private static void summarize(JsonNode nodes){
  for(var n:nodes){var o=(ObjectNode)n;if(o.has("courses")){o.put("course_count",o.path("courses").size());o.remove("courses");}o.remove("bulletin_text");summarize(o.path("children"));}
 }
 /** Screen and memory tools are local and cheap, so they never use up the per-answer tool budget. */
 static boolean local(String name){return Set.of("show_view","save_memory","forget_memory").contains(name);}
 private static void collectCourses(JsonNode n,Set<String> out){if(n==null)return;if(n.has("course"))out.add(n.path("course").asText());for(var c:n.path("children"))collectCourses(c,out);}
 static String code(JsonNode args){
  var m=CODE.matcher(args.path("code").asText(""));if(!m.matches())throw new IllegalArgumentException("code must look like 'CMPSC 465'");
  return m.group(1).toUpperCase(Locale.ROOT)+" "+m.group(2).toUpperCase(Locale.ROOT);
 }
 private static String text(JsonNode args,String field){String v=args.path(field).asText("").strip();return v.isEmpty()?null:v;}
 private static Integer integerArg(JsonNode args,String field){return args.path(field).isNumber()?args.path(field).asInt():null;}
 private static int limit(JsonNode args,int fallback){Integer v=integerArg(args,"limit");return v==null?fallback:Math.max(1,Math.min(MAX_LIST,v));}

 ObjectNode ok(JsonNode data,ArrayNode sources,List<String> warnings){
  var out=mapper.createObjectNode();out.put("ok",true);out.set("data",data);out.set("sources",sources);var w=out.putArray("warnings");warnings.forEach(w::add);return out;
 }
 ObjectNode error(String code,String message){var out=mapper.createObjectNode();out.put("ok",false);out.putObject("error").put("code",code).put("message",message);return out;}
 private ObjectNode source(String type,String ref,String url){var s=mapper.createObjectNode();s.put("type",type);s.put("ref",ref);if(url!=null&&!url.isBlank())s.put("url",url);return s;}

 private ObjectNode fn(String name,String description,ObjectNode properties,String... required){
  var f=mapper.createObjectNode();f.put("name",name);f.put("description",description);
  var params=f.putObject("parameters");params.put("type","object");params.set("properties",properties);
  if(required.length>0){var r=params.putArray("required");for(String q:required)r.add(q);}
  return f;
 }
 private ObjectNode props(Object... pairs){var p=mapper.createObjectNode();for(int i=0;i<pairs.length;i+=2)p.set((String)pairs[i],(JsonNode)pairs[i+1]);return p;}
 private ObjectNode str(String description){return mapper.createObjectNode().put("type","string").put("description",description);}
 private ObjectNode integer(String description){return mapper.createObjectNode().put("type","integer").put("description",description);}
 private ObjectNode integerArray(String description){var a=str(description);a.put("type","array");a.putObject("items").put("type","integer");return a;}
 private ObjectNode strArray(String description){var a=str(description);a.put("type","array");a.putObject("items").put("type","string");return a;}
 private ObjectNode enumOf(String values,String description){var e=str(description);var list=e.putArray("enum");for(String v:values.split("\\|"))list.add(v);return e;}
}
