package dev.lionpathway;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
/** The student's own long-term memories: list them and undo or delete any one. Saving happens only through the agent. */
@RestController @Profile("live") @RequestMapping("/api/memories") public class MemoryController {
 private final AdvisorStore store;
 public MemoryController(AdvisorStore store){this.store=store;}
 @GetMapping public List<Map<String,Object>> list(@AuthenticationPrincipal Jwt jwt){return store.memories(jwt.getSubject());}
 @DeleteMapping("/{id}") public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable long id){if(!store.forgetMemory(jwt.getSubject(),id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Memory not found");}
}
