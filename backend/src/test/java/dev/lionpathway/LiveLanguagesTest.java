package dev.lionpathway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LANGUAGE_LIVE_TEST",matches="1")
class LiveLanguagesTest {
 @Test void answersSyntheticCreditQuestionsInFrenchAndJapanese(){
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  var service=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL","https://flash.luminal.cloud/v1"),env.get("LUMINAL_MODEL","deepseek-ai/DeepSeek-V4.1-Flash"),mapper);
  var r=mapper.createObjectNode();r.putObject("credits").put("required",120).put("completed",72).put("inProgress",15);
  r.put("responseLanguage","fr");
  String fr=service.chat(r,"Combien de crédits ai-je déjà validés, sans compter les cours en cours ? Réponds en une phrase.",List.of());
  assertTrue(fr.contains("72"),fr);assertTrue(fr.toLowerCase(Locale.ROOT).contains("crédit"));
  r.put("responseLanguage","ja");
  String ja=service.chat(r,"履修中の科目を除き、取得済みの単位数はいくつですか？一文で答えてください。",List.of());
  assertTrue(ja.contains("72"),ja);assertTrue(ja.contains("単位"),ja);
 }
}
