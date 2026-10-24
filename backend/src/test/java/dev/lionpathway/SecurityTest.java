package dev.lionpathway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringBootTest @AutoConfigureMockMvc class SecurityTest {
 @Autowired MockMvc mvc;
 @Test void healthIsPublic()throws Exception{mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.live").value(false));}
 @Test void demoNeverAcceptsPrivateReports()throws Exception{mvc.perform(post("/api/reports")).andExpect(status().isForbidden());}
}
