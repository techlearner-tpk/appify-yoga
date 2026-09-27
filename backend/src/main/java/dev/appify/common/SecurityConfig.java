package dev.appify.common;

import dev.appify.identity.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class SecurityConfig {
  @Bean SecurityFilterChain filterChain(HttpSecurity http, TokenService tokens, @Value("${app.web-origin}") String origin) throws Exception {
    var cors = new CorsConfiguration();
    cors.setAllowedOrigins(List.of(origin)); cors.setAllowedMethods(List.of("GET","POST","PUT","PATCH","DELETE","OPTIONS"));
    cors.setAllowedHeaders(List.of("Authorization","Content-Type","X-Request-Id"));
    var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/**", cors);
    return http.csrf(csrf -> csrf.disable()).cors(c -> c.configurationSource(source))
      .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .headers(h -> h.contentSecurityPolicy(c -> c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
      .authorizeHttpRequests(a -> a.requestMatchers("/api/auth/**","/actuator/health/**","/actuator/health","/swagger-ui/**","/swagger-ui","/v3/api-docs/**").permitAll()
        .requestMatchers(HttpMethod.GET,"/api/programs","/api/challenges").permitAll()
        .requestMatchers("/actuator/prometheus").permitAll()
        .requestMatchers("/api/admin/**").hasRole("ADMIN")
        .requestMatchers("/api/instructor/**").hasAnyRole("INSTRUCTOR","ADMIN")
        .anyRequest().authenticated())
      .addFilterBefore(new AuthFilter(tokens), org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class).build();
  }
  static class AuthFilter extends OncePerRequestFilter {
    private final TokenService tokens;
    AuthFilter(TokenService tokens) { this.tokens=tokens; }
    @Override protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
      String requestId=req.getHeader("X-Request-Id"); if(requestId==null || requestId.isBlank()) requestId=UUID.randomUUID().toString();
      MDC.put("requestId",requestId); res.setHeader("X-Request-Id",requestId);
      try {
        String header=req.getHeader(HttpHeaders.AUTHORIZATION);
        if(header!=null && header.startsWith("Bearer ")) {
          try {
            var claims=tokens.parse(header.substring(7));
            UUID id=UUID.fromString(claims.getSubject()); String role=claims.get("role",String.class);
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(id,null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
            MDC.put("userId",id.toString());
          } catch(Exception ignored) { SecurityContextHolder.clearContext(); }
        }
        chain.doFilter(req,res);
      } finally { MDC.clear(); }
    }
  }
}
