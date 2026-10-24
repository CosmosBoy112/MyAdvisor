package dev.lionpathway;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
/** Read-only checks against the loaded catalog tables (see docs/CHANGES-advisor-catalog-db.md "DB 적재 현황"). */
@EnabledIfEnvironmentVariable(named="LIONPATHWAY_LIVE_TEST", matches="1")
class LiveCatalogDbTest {
 @Test void dataSciencesReportGetsCatalogAndOtherMajorsDoNot()throws Exception{
  var env=Dotenv.configure().ignoreIfMissing().load();var mapper=new ObjectMapper();
  try(var ds=new DatabaseConfig().dataSource(env.get("TIGER_DATABASE_URL"),env.get("TIGER_DATABASE_USER"),env.get("TIGER_DATABASE_PASSWORD"))){
   var service=new CatalogDbService(new JdbcTemplate(ds),mapper);
   var report=mapper.readTree("""
   {"major":"Data Sciences (BS)","catalogYear":"Fall 2024","requirements":[{"title":"Computational Option List A"}]}""");
   var out=service.context(report,"What does CMPSC 360 unlock, and what do I need for CMPSC 465?");
   assertEquals("matched_year_differs",out.path("status").asText());assertEquals("2024-25",out.path("reportCatalogYear").asText());
   assertEquals("Computational Data Sciences",out.path("option").asText());
   String text=out.toString();
   assertTrue(text.contains("Computational Option List A"));assertTrue(text.contains("Fundamentals of Computer Vision"));
   assertFalse(text.contains("Application Focus"),"other options must be excluded once the option is known");
   assertTrue(text.contains("suggested_academic_plan.COMPUTATIONAL"));assertTrue(text.contains("DTSCE-handbook-2024-2025"));
   assertTrue(out.path("entranceToMajor").path("requirements").size()>0);
   var courses=out.path("courses");assertEquals("CMPSC 360",courses.path(0).path("code").asText());
   assertTrue(courses.path(0).path("unlocks").toString().contains("CMPSC 465"));
   assertTrue(courses.path(1).path("prerequisite_tree").toString().contains("MATH 311W"));
   assertEquals("major_not_in_catalog",service.context(mapper.readTree("{\"major\":\"Comp Sci - Univ Park (BS)\"}"),"Explain List A").path("status").asText());
   var cs=service.context(mapper.readTree("{\"major\":\"Computer Science (BS)\",\"catalogYear\":\"2026-27\"}"),"Explain Department List");
   assertEquals("matched",cs.path("status").asText());assertEquals("CMPSC_BS",cs.path("programCode").asText());
   String cstext=cs.toString();assertFalse(cstext.contains("bulletin_text\":\"CMPSC catalog metadata"),"loader metadata must not be presented as Bulletin text");
   assertTrue(cstext.contains("not_listed_in_database"));assertTrue(cstext.contains("value_omitted"));
   System.out.println("LIVE_CHECK: catalog DB context size DS="+text.length()+" CMPSC="+cstext.length()+" chars");
  }
 }
}
