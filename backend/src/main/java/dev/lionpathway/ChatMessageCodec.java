package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
/** Versioned envelope in the existing TEXT column (answer, web evidence, agent screen); legacy plain messages stay readable. */
final class ChatMessageCodec {
 private static final String PREFIX="LIONPATHWAY_CHAT_V1\n";
 private static final ObjectMapper MAPPER=new ObjectMapper();
 static String encode(String answer,JsonNode web){return encode(answer,web,null);}
 static String encode(String answer,JsonNode web,JsonNode view){
  if(web==null&&view==null)return answer;
  var node=MAPPER.createObjectNode();node.put("answer",answer);if(web!=null)node.set("webResearch",web);if(view!=null)node.set("view",view);return PREFIX+node;
 }
 static Map<String,Object> decode(Map<String,Object> row){
  var result=new LinkedHashMap<String,Object>(row);String content=String.valueOf(row.get("content"));
  if("assistant".equals(row.get("role"))&&content.startsWith(PREFIX))try{
   var node=MAPPER.readTree(content.substring(PREFIX.length()));if(node.path("answer").isTextual()){result.put("content",node.path("answer").asText());if(node.has("webResearch"))result.put("webResearch",node.path("webResearch"));if(node.has("view"))result.put("view",node.path("view"));}
  }catch(Exception ignored){}
  return result;
 }
}
