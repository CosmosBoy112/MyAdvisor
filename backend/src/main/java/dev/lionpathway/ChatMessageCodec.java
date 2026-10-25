package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.*;
/** Versioned envelope in the existing TEXT column; legacy plain messages stay readable. */
final class ChatMessageCodec {
 private static final String PREFIX="LIONPATHWAY_CHAT_V1\n";
 private static final ObjectMapper MAPPER=new ObjectMapper();
 static String encode(String answer,JsonNode web){
  if(web==null)return answer;
  var node=MAPPER.createObjectNode();node.put("answer",answer);node.set("webResearch",web);return PREFIX+node;
 }
 static Map<String,Object> decode(Map<String,Object> row){
  var result=new LinkedHashMap<String,Object>(row);String content=String.valueOf(row.get("content"));
  if("assistant".equals(row.get("role"))&&content.startsWith(PREFIX))try{
   var node=MAPPER.readTree(content.substring(PREFIX.length()));if(node.path("answer").isTextual()){result.put("content",node.path("answer").asText());result.put("webResearch",node.path("webResearch"));}
  }catch(Exception ignored){}
  return result;
 }
}
