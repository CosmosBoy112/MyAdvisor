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
 private final CatalogService catalog;private final PdfReader pdf;private final GeminiService gemini;private final ModelRouter router;private final AnalysisValidator validator;private final ReportRepository repository;
 public ReportController(PdfReader pdf,GeminiService gemini,ModelRouter router,AnalysisValidator validator,ReportRepository repository,CatalogService catalog){this.catalog=catalog;this.pdf=pdf;this.gemini=gemini;this.router=router;this.validator=validator;this.repository=repository;}
 @PostMapping(consumes="multipart/form-data") public JsonNode upload(@AuthenticationPrincipal Jwt jwt,@RequestParam("file")MultipartFile file,@RequestParam(defaultValue="")String intent)throws java.io.IOException{
  if(file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Please upload a non-empty PDF of at most 10 MB.");
  if(intent.length()>1000)throw new IllegalArgumentException("Please keep your study goal under 1,000 characters.");
  UUID id=UUID.randomUUID();long started=System.nanoTime();String stage="pdf";
  try {
  log.info("Analysis {} stage=pdf",id);
  var pages=pdf.read(file.getBytes());stage="ai";log.info("Analysis {} stage=ai elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);
  var report=catalog.enrich(validator.validate(gemini.analyze(pages,intent),pages),pages);stage="database";log.info("Analysis {} stage=database elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);
  String name=Optional.ofNullable(file.getOriginalFilename()).orElse("report.pdf").replaceAll("[^a-zA-Z0-9._ -]","_");if(name.length()>200)name=name.substring(name.length()-200);
  report.put("id",id.toString());report.put("demo",false);report.put("fileName",name);report.put("intent",intent);repository.save(id,jwt.getSubject(),name,report);log.info("Analysis {} stage=complete elapsedMs={}",id,(System.nanoTime()-started)/1_000_000);return report;
  }catch(RuntimeException | java.io.IOException e){log.warn("Analysis {} stage={} failed={} elapsedMs={}",id,stage,e.getClass().getSimpleName(),(System.nanoTime()-started)/1_000_000);throw e;}
 }
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt){return repository.list(jwt.getSubject());}
 @GetMapping("/{id}/messages")public List<Map<String,Object>> messages(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){repository.get(id,jwt.getSubject());return repository.history(id);}
 @GetMapping("/{id}")public JsonNode get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return catalog.forRead(repository.get(id,jwt.getSubject()));}
 @PostMapping("/{id}/chat")public Map<String,String> chat(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Question body){var report=catalog.forRead(repository.get(id,jwt.getSubject()));var answer=router.chat(report,body.question(),repository.history(id));repository.conversation(id,body.question(),answer.text());return Map.of("answer",answer.text(),"provider",answer.provider());}
 @DeleteMapping("/{id}")public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){repository.delete(id,jwt.getSubject());}
 public record Question(@NotBlank @Size(max=2000)String question){}
}
