package dev.lionpathway;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
@Configuration
public class SecurityConfig {
 @Bean CorsConfigurationSource cors(@Value("${app.frontend-origin}") String origin){
  var config=new CorsConfiguration(); config.setAllowedOrigins(List.of(origin));config.setAllowedMethods(List.of("GET","POST","DELETE","OPTIONS"));config.setAllowedHeaders(List.of("Authorization","Content-Type"));config.setMaxAge(3600L);
  var source=new UrlBasedCorsConfigurationSource();source.registerCorsConfiguration("/**",config);return source;
 }
 private HttpSecurity base(HttpSecurity http) throws Exception {return http.csrf(c->c.disable()).cors(c->{}).sessionManagement(c->c.sessionCreationPolicy(SessionCreationPolicy.STATELESS));}
 @Bean @Profile("!live") SecurityFilterChain demoSecurity(HttpSecurity http)throws Exception{return base(http).authorizeHttpRequests(a->a.requestMatchers("/api/health").permitAll().anyRequest().denyAll()).build();}
 @Bean @Profile("live") SecurityFilterChain liveSecurity(HttpSecurity http)throws Exception{return base(http).authorizeHttpRequests(a->a.requestMatchers("/api/health").permitAll().anyRequest().authenticated()).oauth2ResourceServer(o->o.jwt(j->{})).build();}
 @Bean @Profile("live") JwtDecoder decoder(@Value("${AUTH0_ISSUER}")String issuer,@Value("${AUTH0_AUDIENCE}")String audience){
  NimbusJwtDecoder decoder=JwtDecoders.fromIssuerLocation(issuer);
  OAuth2TokenValidator<Jwt> audienceValidator=jwt->jwt.getAudience().contains(audience)?OAuth2TokenValidatorResult.success():OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Invalid audience",null));
  decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),audienceValidator));return decoder;
 }
}
