package dev.lionpathway;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_MINOR_LIVE_TEST",matches="1")
class LiveMathMinorTest {
 @Test void explainsSyntheticMinorEstimateUsingLuminal()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();var r=mapper.createObjectNode().put("major","Computer Science").put("campus","University Park").put("catalogYear","Spring 2023");
  var c=r.putObject("courseInventory").put("method","pdf_course_rows_v1").putArray("courses");
  for(String code:List.of("MATH 140","MATH 141","MATH 230","MATH 441"))c.addObject().put("code",code).put("term","FA 2025").put("grade","A").put("credits",code.equals("MATH 441")?3:4);
  c.addObject().put("code","MATH 220").put("term","FA 2025").put("grade","A").put("credits",2);
  r.set("minorAssessment",new MathMinorService(mapper,Optional.empty()).evaluate(r));
  var luminal=new LuminalService(env.get("LUMINAL_API_KEY",""),env.get("LUMINAL_BASE_URL"),env.get("LUMINAL_MODEL"),mapper);
  String answer=luminal.chat(r,"Math minor 하려면 추가로 몇 학점이 필요한가요? 참고 학년도와 아직 확인해야 할 조건도 한국어로 150단어 이내로 설명해 주세요.",List.of());
  assertTrue(answer.contains("9"));assertTrue(answer.contains("2026"));assertTrue(answer.contains("6"));assertTrue(answer.contains("bulletins.psu.edu"));System.out.println("LIVE_MATH_MINOR: expected numerical gap, catalog year, unique-credit condition and official citation returned.");
 }
}
