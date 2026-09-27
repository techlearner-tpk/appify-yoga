package dev.appify.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class RateLimitInterceptor implements HandlerInterceptor,WebMvcConfigurer {
  private static final Logger log=LoggerFactory.getLogger(RateLimitInterceptor.class);
  private static final DefaultRedisScript<Long> SCRIPT=new DefaultRedisScript<>("local count=redis.call('INCR',KEYS[1]); if count==1 then redis.call('EXPIRE',KEYS[1],ARGV[1]); end; return count",Long.class);
  private final StringRedisTemplate redis;
  public RateLimitInterceptor(StringRedisTemplate redis) {this.redis=redis;}
  @Override public void addInterceptors(InterceptorRegistry registry) {registry.addInterceptor(this).addPathPatterns("/api/**");}
  @Override public boolean preHandle(HttpServletRequest req,HttpServletResponse res,Object handler) throws Exception {
    if(!req.getMethod().equals("POST") && !req.getMethod().equals("PUT") && !req.getMethod().equals("DELETE")) return true;
    String path=req.getRequestURI(); int limit; int seconds;
    // The web BFF shares one backend IP for all visitors; account-level controls apply in identity.
    if(path.equals("/api/auth/login")) {limit=1000;seconds=300;}
    else if(path.equals("/api/auth/register")) {limit=300;seconds=300;}
    else if(path.startsWith("/api/attendance/")) {limit=180;seconds=60;}
    else if(path.startsWith("/api/admin/")) {limit=60;seconds=60;}
    else if(path.contains("referral")) {limit=10;seconds=300;}
    else return true;
    var auth=SecurityContextHolder.getContext().getAuthentication();
    String actor=auth!=null && auth.getPrincipal() instanceof java.util.UUID?auth.getPrincipal().toString():req.getRemoteAddr();
    String key="rate:"+path+":"+actor;
    try {
      Long count=redis.execute(SCRIPT,List.of(key),Integer.toString(seconds));
      if(count!=null && count>limit) {res.setHeader("Retry-After",Integer.toString(seconds));res.sendError(HttpStatus.TOO_MANY_REQUESTS.value(),"Rate limit exceeded");return false;}
    } catch(Exception e) {log.warn("rate_limit_unavailable path={}",path);}
    return true;
  }
}
