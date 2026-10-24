package dev.lionpathway;
import java.io.IOException;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

final class BoundedHttp {
 private BoundedHttp(){}
 static HttpResponse<String> send(HttpClient client,HttpRequest request,Duration timeout)throws IOException,InterruptedException{
  var pending=client.sendAsync(request,HttpResponse.BodyHandlers.ofString());
  try{return pending.get(timeout.toMillis(),TimeUnit.MILLISECONDS);}
  catch(TimeoutException e){pending.cancel(true);throw new HttpTimeoutException("AI response deadline exceeded");}
  catch(InterruptedException e){pending.cancel(true);throw e;}
  catch(ExecutionException e){
   if(e.getCause() instanceof IOException io)throw io;
   throw new IOException("AI request failed",e.getCause());
  }
 }
}
