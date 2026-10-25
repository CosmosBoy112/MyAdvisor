package dev.lionpathway;
import org.junit.jupiter.api.Test;
import jakarta.validation.Validation;
import static org.junit.jupiter.api.Assertions.*;
class SupportedLanguagesTest {
 @Test void tenLanguageCodesAreAcceptedByChatValidation(){
  try(var factory=Validation.buildDefaultValidatorFactory()){
   var validator=factory.getValidator();
   for(String code:"en ko zh ja es fr de pt ru hi".split(" ")){
    assertTrue(SupportedLanguages.supports(code));
    assertFalse(SupportedLanguages.name(code).isBlank());
    assertTrue(validator.validate(new ReportController.Question("Which courses?",code,true)).isEmpty());
   }
   assertFalse(validator.validate(new ReportController.Question("Which courses?","xx",true)).isEmpty());
  }
  assertFalse(SupportedLanguages.supports("xx"));
  assertEquals("English",SupportedLanguages.name(null));
 }
}
