package dev.lionpathway;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
@RestControllerAdvice public class ApiErrors {
 @ExceptionHandler(IllegalArgumentException.class)ResponseEntity<?> bad(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(MethodArgumentNotValidException.class)ResponseEntity<?> validation(){return ResponseEntity.badRequest().body(Map.of("message","Please enter a question between 1 and 2,000 characters."));}
 @ExceptionHandler(MaxUploadSizeExceededException.class)ResponseEntity<?> size(){return ResponseEntity.status(413).body(Map.of("message","Please upload a PDF of at most 10 MB."));}
 @ExceptionHandler(AnalysisTimeout.class)ResponseEntity<?> timeout(AnalysisTimeout e){return ResponseEntity.status(504).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(UpstreamFailure.class)ResponseEntity<?> upstream(UpstreamFailure e){return ResponseEntity.status(502).body(Map.of("message",e.getMessage()));}
 @ExceptionHandler(ResponseStatusException.class)ResponseEntity<?> status(ResponseStatusException e){return ResponseEntity.status(e.getStatusCode()).body(Map.of("message",e.getReason()==null?"Request failed":e.getReason()));}
 @ExceptionHandler(Exception.class)ResponseEntity<?> unexpected(Exception e){return ResponseEntity.status(500).body(Map.of("message","The request could not be completed. Check the server configuration and try again."));}
}
