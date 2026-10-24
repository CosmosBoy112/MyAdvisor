package dev.lionpathway;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class BoundedHttpTest {
 @Test void cancelsRequestWhenBodyNeverCompletes(){
  var client=mock(HttpClient.class);var pending=new CompletableFuture<HttpResponse<String>>();
  when(client.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(pending);
  assertThrows(HttpTimeoutException.class,()->BoundedHttp.send(client,HttpRequest.newBuilder(URI.create("https://example.com")).build(),Duration.ofMillis(20)));
  assertTrue(pending.isCancelled());
 }
 @Test void propagatesTransportFailure(){
  var client=mock(HttpClient.class);var pending=new CompletableFuture<HttpResponse<String>>();pending.completeExceptionally(new java.io.IOException("Synthetic connection failure"));
  when(client.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenReturn(pending);
  assertThrows(java.io.IOException.class,()->BoundedHttp.send(client,HttpRequest.newBuilder(URI.create("https://example.com")).build(),Duration.ofSeconds(1)));
 }
}
