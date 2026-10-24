package dev.lionpathway;
import java.nio.file.*;
import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
@SpringBootApplication(exclude=DataSourceAutoConfiguration.class)
public class Application {
 public static void loadLocalEnvironment(){
  String directory=Files.isRegularFile(Path.of(".env"))?".":Files.isRegularFile(Path.of("backend/.env"))?"backend":".";
  try {
   var dotenv=Dotenv.configure().directory(directory).ignoreIfMissing().load();
   for(var entry:dotenv.entries())if(System.getProperty(entry.getKey())==null && System.getenv(entry.getKey())==null)System.setProperty(entry.getKey(),entry.getValue());
   if(System.getProperty("spring.profiles.active")==null && System.getenv("SPRING_PROFILES_ACTIVE")==null && dotenv.get("SPRING_PROFILES_ACTIVE")!=null)System.setProperty("spring.profiles.active",dotenv.get("SPRING_PROFILES_ACTIVE"));
  } catch(Exception e) {throw new IllegalStateException("Could not read .env. Check KEY=value syntax; values are not printed for privacy.");}
 }
 public static void main(String[] args){loadLocalEnvironment();SpringApplication.run(Application.class,args);}
}
