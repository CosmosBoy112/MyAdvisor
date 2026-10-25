package dev.lionpathway;
import java.util.Map;
final class SupportedLanguages {
 static final String PATTERN="en|ko|zh|ja|es|fr|de|pt|ru|hi";
 private static final Map<String,String> NAMES=Map.of("en","English","ko","Korean","zh","Simplified Chinese","ja","Japanese","es","Spanish","fr","French","de","German","pt","Portuguese","ru","Russian","hi","Hindi");
 static boolean supports(String code){return code!=null&&NAMES.containsKey(code);}
 static String name(String code){return code==null?"English":NAMES.getOrDefault(code,"English");}
 private SupportedLanguages(){}
}
