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
 private final PdfReader pdf;private final GeminiService gemini;private final AnalysisValidator validator;private final ReportRepository repository;
 public ReportController(PdfReader pdf,GeminiService gemini,AnalysisValidator validator,ReportRepository repository){this.pdf=pdf;this.gemini=gemini;this.validator=validator;this.repository=repository;}
 @PostMapping(consumes="multipart/form-data") public JsonNode upload(@AuthenticationPrincipal Jwt jwt,@RequestParam("file")MultipartFile file,@RequestParam(defaultValue="")String intent)throws java.io.IOException{
  if(file.isEmpty()||file.getSize()>10*1024*1024)throw new IllegalArgumentException("Please upload a non-empty PDF of at most 10 MB.");
  if(intent.length()>1000)throw new IllegalArgumentException("Please keep your study goal under 1,000 characters.");
  var pages=pdf.read(file.getBytes());var report=validator.validate(gemini.analyze(pages,intent),pages);UUID id=UUID.randomUUID();String name=Optional.ofNullable(file.getOriginalFilename()).orElse("report.pdf").replaceAll("[^a-zA-Z0-9._ -]","_");if(name.length()>200)name=name.substring(name.length()-200);
  report.put("id",id.toString());report.put("demo",false);report.put("fileName",name);report.put("intent",intent);repository.save(id,jwt.getSubject(),name,report);return report;
 }
 @GetMapping("/{id}")public JsonNode get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return repository.get(id,jwt.getSubject());}
 @PostMapping("/{id}/chat")public Map<String,String> chat(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Question body){var report=repository.get(id,jwt.getSubject());String answer=gemini.chat(report,body.question(),repository.history(id));repository.conversation(id,body.question(),answer);return Map.of("answer",answer);}
 @DeleteMapping("/{id}")public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){repository.delete(id,jwt.getSubject());}
 public record Question(@NotBlank @Size(max=2000)String question){}
}
