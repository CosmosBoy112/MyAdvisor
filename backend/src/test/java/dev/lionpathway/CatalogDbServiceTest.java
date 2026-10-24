package dev.lionpathway;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CatalogDbServiceTest {
 private final ObjectMapper mapper=new ObjectMapper();
 private final List<CatalogDbService.Program> programs=List.of(new CatalogDbService.Program(1,"DATSC_BS","Data Sciences, B.S.","2026-27",123.0,"https://bulletins.psu.edu/",
  Map.of("APPLIED",1L,"COMPUTATIONAL",2L,"STATISTICAL",3L),Map.of("APPLIED","Applied Data Sciences","COMPUTATIONAL","Computational Data Sciences","STATISTICAL","Statistical Modeling Data Sciences")),
  new CatalogDbService.Program(2,"CMPSC_BS","Computer Science, B.S. (Engineering)","2026-27",127.0,"https://bulletins.psu.edu/",Map.of(),Map.of()));
 private Optional<CatalogDbService.Match> match(String major)throws Exception{return CatalogDbService.matchMajor(major,programs,mapper.readTree("[]"));}
 @Test void matchesLoadedMajorSpellings()throws Exception{
  for(String major:List.of("Data Sciences (BS)","Data Sciences, B.S.","Data Science - University Park","DATA SCIENCES B S (Engineering)"))assertTrue(match(major).isPresent(),major);
  assertEquals("COMPUTATIONAL",match("Data Sciences - Computational Option (BS)").orElseThrow().option());
  assertEquals("COMPUTATIONAL",match("Computational Data Sciences").orElseThrow().option());
  assertNull(match("Data Sciences (BS)").orElseThrow().option());
  for(String major:List.of("Computer Science (BS)","Computer Science, B.S.","Computer Science - University Park"))assertEquals("CMPSC_BS",match(major).orElseThrow().program().code(),major);
 }
 @Test void rejectsOtherMajorsAndMinors()throws Exception{
  for(String major:Arrays.asList("Comp Sci - Univ Park (BS)","Computer Engineering (BS)","Computer Science Minor","Data Sciences Minor","Statistics (BS)","Data Analytics","",null))assertTrue(match(major).isEmpty(),String.valueOf(major));
 }
 @Test void optionComesFromASingleReportRequirementTitle()throws Exception{
  var reqs=mapper.readTree("[{\"title\":\"Computational Option List A\"},{\"title\":\"Department List\"}]");
  var m=CatalogDbService.matchMajor("Data Sciences (BS)",programs,reqs).orElseThrow();assertEquals("COMPUTATIONAL",m.option());assertEquals("report_requirements",m.optionSource());
  var both=mapper.readTree("[{\"title\":\"Computational Option List A\"},{\"title\":\"Applied Data Sciences focus\"}]");
  assertNull(CatalogDbService.matchMajor("Data Sciences (BS)",programs,both).orElseThrow().option());
 }
 @Test void normalizesRequirementYears(){
  assertEquals("2026-27",CatalogDbService.catalogYear("2026-27"));assertEquals("2026-27",CatalogDbService.catalogYear("2026–2027"));
  assertEquals("2024-25",CatalogDbService.catalogYear("Fall 2024"));assertEquals("2026-27",CatalogDbService.catalogYear("Summer 2026"));
  assertEquals("2022-23",CatalogDbService.catalogYear("Spring 2023"));assertNull(CatalogDbService.catalogYear("unknown"));assertNull(CatalogDbService.catalogYear(null));
 }
 @Test void extractsCourseCodesFromQuestions(){
  assertEquals(List.of("CMPSC 465","DS 410","STAT 414"),CatalogDbService.courseCodes("cmpsc465 들으려면? DS 410 and STAT 414, again CMPSC 465"));
  assertTrue(CatalogDbService.courseCodes("How many credits are left?").isEmpty());
 }
 @Test void databaseErrorsFallBackToReportOnly()throws Exception{
  var jdbc=mock(JdbcTemplate.class);when(jdbc.queryForList(anyString())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("down"));
  var out=new CatalogDbService(jdbc,mapper).context(mapper.readTree("{\"major\":\"Data Sciences (BS)\"}"),"Explain List A");
  assertEquals("catalog_unavailable",out.path("status").asText());
 }
}
