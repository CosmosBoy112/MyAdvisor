package dev.lionpathway;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.*;
@RestController public class HealthController {
 private final Environment env;public HealthController(Environment env){this.env=env;}
 @GetMapping("/api/health") public Map<String,Object> health(){return Map.of("status","ok","live",env.matchesProfiles("live"));}
}
