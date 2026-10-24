package dev.lionpathway;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
// Assumes Luminal exposes an OpenAI-compatible /chat/completions endpoint authenticated with a Bearer API key.
@Service @Profile("live") public class LuminalService {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LuminalService.class);
 private static final Duration TIMEOUT=Duration.ofSeconds(60);
 private final String apiKey,baseUrl,model;private final ObjectMapper mapper;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
 public LuminalService(@Value("${LUMINAL_API_KEY:}")String key,@Value("${LUMINAL_BASE_URL:https://flash.luminal.cloud/v1}")String baseUrl,@Value("${LUMINAL_MODEL:deepseek-ai/DeepSeek-V4.1-Flash}")String model,ObjectMapper mapper){if(!baseUrl.startsWith("https://"))throw new IllegalArgumentException("LUMINAL_BASE_URL must use https");if(!model.matches("[A-Za-z0-9._/:-]+"))throw new IllegalArgumentException("Invalid Luminal model name");this.apiKey=key.strip();this.baseUrl=baseUrl.replaceAll("/+$","");this.model=model;this.mapper=mapper;}
 public boolean configured(){return !apiKey.isEmpty();}
 public String chat(JsonNode report,String question,List<Map<String,Object>> history){
  return complete(GeminiService.CHAT_SYSTEM,GeminiService.chatInput(report,question,history));
 }
 private String complete(String system,String text){
  if(!configured())throw new UpstreamFailure("Luminal is not configured on the server.");
  long started=System.nanoTime();
  try{
   var payload=Map.of("model",model,"temperature",0.2,"messages",List.of(Map.of("role","system","content",system),Map.of("role","user","content",text)));
   var request=HttpRequest.newBuilder(URI.create(baseUrl+"/chat/completions")).timeout(TIMEOUT).header("Content-Type","application/json").header("Authorization","Bearer "+apiKey).POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload))).build();
   var response=BoundedHttp.send(client,request,TIMEOUT);
   log.info("Luminal response model={} status={} elapsedMs={}",model,response.statusCode(),(System.nanoTime()-started)/1_000_000);
   if(response.statusCode()!=200){
    String message=switch(response.statusCode()){
     case 400 -> "The Luminal request was rejected. Check LUMINAL_MODEL on the server.";
     case 401,403 -> "Luminal could not authorize this request. Check LUMINAL_API_KEY on the server.";
     case 404 -> "The Luminal endpoint or model was not found. Check LUMINAL_BASE_URL and LUMINAL_MODEL on the server.";
     case 429 -> "The Luminal usage limit was reached. Wait a moment and try again.";
     default -> "Luminal is temporarily unavailable. Please try again later.";
    };throw new UpstreamFailure(message);
   }
   JsonNode choice=mapper.readTree(response.body()).path("choices").path(0);
   if("length".equals(choice.path("finish_reason").asText()))throw new UpstreamFailure("The Luminal response was cut off. Please ask a narrower question.");
   String answer=choice.path("message").path("content").asText("");
   if(answer.isBlank())throw new UpstreamFailure("Luminal did not return an answer. Try a different question.");return answer;
  }catch(java.net.http.HttpTimeoutException e){throw new AnalysisTimeout("Luminal did not respond within 60 seconds. Please retry in a moment.");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new UpstreamFailure("The Luminal request was interrupted.");}catch(java.io.IOException e){throw new UpstreamFailure("Could not reach Luminal. Please try again.");}
 }
}
