package dev.lionpathway;
import javax.sql.DataSource;
import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
@Configuration @Profile("live") public class DatabaseConfig {
 @Bean(destroyMethod="close") HikariDataSource dataSource(@Value("${TIGER_DATABASE_URL}") String url,@Value("${TIGER_DATABASE_USER}") String user,@Value("${TIGER_DATABASE_PASSWORD}") String password){
  if(!url.startsWith("jdbc:postgresql://")||!url.matches(".*[?&]sslmode=(require|verify-ca|verify-full)(&.*)?$"))throw new IllegalArgumentException("TigerData requires a PostgreSQL JDBC URL with TLS enabled.");
  var config=new HikariConfig();config.setJdbcUrl(url);config.setUsername(user);config.setPassword(password);config.setMaximumPoolSize(5);config.setConnectionTimeout(15000);return new HikariDataSource(config);
 }
 @Bean JdbcTemplate jdbc(DataSource ds){new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(ds);return new JdbcTemplate(ds);}
}
