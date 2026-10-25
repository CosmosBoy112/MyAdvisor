package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @Profile("live") @RequestMapping("/api/reports") public class ReportController {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(ReportController.class);
 private final MathMinorService minor;private final CatalogService catalog;private final CatalogDbService catalogDb;private final PdfReader pdf;private final GeminiService gemini;private final ModelRouter router;private final AnalysisValidator validator;private final ReportRepository repository;
 public ReportController(PdfReader pdf,GeminiService gemini,ModelRouter router,AnalysisValidator validator,ReportRepository repository,CatalogService catalog,CatalogDbService catalogDb){this(pdf,gemini,router,validator,repository,catalog,catalogDb,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public ReportController(PdfReader pdf,GeminiService gemini,ModelRouter router,AnalysisValidator validator,ReportRepository repository,CatalogService catalog,CatalogDbService catalogDb,MathMinorService minor){this.minor=minor;this.catalog=catalog;this.catalogDb=catalogDb;this.pdf=pdf;this.gemini=gemini;this.router=router;this.validator=validator;this.repository=repository;}
 @PostMapping(consumes="multipart/form-data") public JsonNode upload(@AuthenticationPrincipal Jwt jwt,@RequestParam("file")MultipartFile file,@RequestParam(defaultValue="")String intent,@RequestParam(defaultValue="en")String language)throws java.io.IOException{
  if(!SupportedLanguages.supports(language))throw new IllegalArgumentException("Unsupported display language.");
  if(file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Please upload a non-empty PDF of at most 10 MB.");
  if(intent.length()>1000)throw new IllegalArgumentException("Please keep your study goal under 1,000 characters.");
  UUID id=UUID.randomUUID();long started=System.nanoTime();String stage="pdf";
  try {
  log.info("Analysis {} stage=pdf",id);
  var pages=pdf.read(file.getBytes());stage="ai";log.info("Analysis {} stage=ai elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);
  var report=catalog.enrich(validator.validate(gemini.analyze(pages,intent,language),pages),pages);stage="database";log.info("Analysis {} stage=database elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("report.pdf").replaceAll("[^a-zA-Z0-9._ -]","_");if(name.length()>200)name=name.substring(name.length()-200);
  report.put("id",id.toString());report.put("demo",false);report.put("fileName",name);report.put("intent",intent);report.put("responseLanguage",language);withMinor(report);repository.save(id,jwt.getSubject(),name,report);log.info("Analysis {} stage=complete elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);return report;
  }catch(RuntimeException | java.io.IOException e){log.warn("Analysis {} stage={} failed={} elapsedMs={}",id,stage,e.getClass().getSimpleName(),(System.nanoTime()-started)/1_000_000);throw e;}
 }
 public JsonNode upload(Jwt jwt,MultipartFile file,String intent)throws java.io.IOException{return upload(jwt,file,intent,"en");}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt){return repository.list(jwt.getSubject());}
 @GetMapping("/{id}/messages")public List<Map<String,Object>> messages(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){repository.get(id,jwt.getSubject());return repository.history(id);}
 @GetMapping("/{id}")public JsonNode get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return withMinor(catalog.forRead(repository.get(id,jwt.getSubject())));}
 @PostMapping("/{id}/chat")public Map<String,Object> chat(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Question body){var report=withMinor(catalog.forRead(repository.get(id,jwt.getSubject())),body.question());((com.fasterxml.jackson.databind.node.ObjectNode)report).put("responseLanguage",body.language()==null?"en":body.language());((com.fasterxml.jackson.databind.node.ObjectNode)report).set("catalogDb",catalogDb.context(report,body.question()));((com.fasterxml.jackson.databind.node.ObjectNode)report).put("webSearchEnabled",body.webSearch()==null||body.webSearch());var answer=router.chat(report,body.question(),repository.history(id));repository.conversation(id,body.question(),ChatMessageCodec.encode(answer.text(),answer.webResearch()));var response=new LinkedHashMap<String,Object>();response.put("answer",answer.text());response.put("provider",answer.provider());if(answer.webResearch()!=null)response.put("webResearch",answer.webResearch());return response;}
 private JsonNode withMinor(JsonNode report){return withMinor(report,"");}
 private JsonNode withMinor(JsonNode report,String question){((com.fasterxml.jackson.databind.node.ObjectNode)report).remove("minorAssessment");if(minor!=null&&MathMinorService.relevant(report,question))((com.fasterxml.jackson.databind.node.ObjectNode)report).set("minorAssessment",minor.evaluate(report));return report;}
 @DeleteMapping("/{id}")public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){repository.delete(id,jwt.getSubject());}
 public record Question(@NotBlank @Size(max=2000)String question,@Pattern(regexp=SupportedLanguages.PATTERN)String language,Boolean webSearch){public Question(String question){this(question,"en",true);}public Question(String question,String language){this(question,language,true);}}
}
