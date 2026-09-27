package dev.appify.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

@RestController @RequestMapping("/api/auth")
public class IdentityController {
  private final JdbcTemplate db; private final TokenService tokens; private final StringRedisTemplate redis; private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder();
  private static final DefaultRedisScript<Long> LOGIN_LIMIT=new DefaultRedisScript<>("local count=redis.call('INCR',KEYS[1]); if count==1 then redis.call('EXPIRE',KEYS[1],300); end; return count",Long.class);
  public IdentityController(JdbcTemplate db,TokenService tokens,StringRedisTemplate redis) { this.db=db; this.tokens=tokens;this.redis=redis; }
  public record Register(@Email @NotBlank String email,@Size(min=10) String password,@NotBlank String displayName,String referralCode) {}
  public record Login(@Email @NotBlank String email,@NotBlank String password) {}
  public record Refresh(@NotBlank String refreshToken) {}
  @PostMapping("/register") @Transactional
  public Map<String,Object> register(@Valid @RequestBody Register body) {
    UUID id=UUID.randomUUID(); String email=body.email().trim().toLowerCase();
    try { db.update("insert into app_user(id,email,password_hash,display_name,referral_code) values(?,?,?,?,?)",id,email,encoder.encode(body.password()),body.displayName().trim(),id.toString().substring(0,8).toUpperCase()); }
    catch(DuplicateKeyException e) { throw new ResponseStatusException(HttpStatus.CONFLICT,"Email already registered"); }
    if(body.referralCode()!=null && !body.referralCode().isBlank()) {
      var ref=db.query("select id from app_user where referral_code=?",(rs,n)->(UUID)rs.getObject(1),body.referralCode().toUpperCase());
      if(!ref.isEmpty() && !ref.get(0).equals(id)) db.update("insert into referral(id,referrer_id,invitee_id) values(?,?,?)",UUID.randomUUID(),ref.get(0),id);
    }
    db.update("insert into streak(user_id) values(?)",id);
    db.update("insert into membership_entitlement(user_id,status,valid_until) values(?,'TRIAL',now()+interval '30 days')",id);
    return session(id,"USER");
  }
  @PostMapping("/login")
  public Map<String,Object> login(@Valid @RequestBody Login body) {
    String email=body.email().trim().toLowerCase();
    try {Long attempts=redis.execute(LOGIN_LIMIT,List.of("login-account:"+email));if(attempts!=null && attempts>20) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Too many login attempts");}
    catch(ResponseStatusException e) {throw e;} catch(Exception ignored) { /* Database authentication remains available during Redis outage. */ }
    var rows=db.query("select id,password_hash,role from app_user where email=? and disabled=false",(rs,n)->new Object[]{rs.getObject(1),rs.getString(2),rs.getString(3)},email);
    if(rows.isEmpty() || !encoder.matches(body.password(),(String)rows.get(0)[1])) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
    return session((UUID)rows.get(0)[0],(String)rows.get(0)[2]);
  }
  @PostMapping("/refresh")
  public Map<String,Object> refresh(@Valid @RequestBody Refresh body) {
    var rows=db.query("select r.user_id,u.role from refresh_token r join app_user u on u.id=r.user_id where r.token_hash=? and r.revoked=false and r.expires_at>now() and u.disabled=false",(rs,n)->new Object[]{rs.getObject(1),rs.getString(2)},hash(body.refreshToken()));
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid refresh token");
    db.update("update refresh_token set revoked=true where token_hash=?",hash(body.refreshToken()));
    return session((UUID)rows.get(0)[0],(String)rows.get(0)[1]);
  }
  @PostMapping("/logout") public void logout(@Valid @RequestBody Refresh body) { db.update("update refresh_token set revoked=true where token_hash=?",hash(body.refreshToken())); }
  private Map<String,Object> session(UUID id,String role) {
    String refresh=UUID.randomUUID().toString()+UUID.randomUUID();
    db.update("insert into refresh_token(id,user_id,token_hash,expires_at) values(?,?,?,?)",UUID.randomUUID(),id,hash(refresh),java.sql.Timestamp.from(Instant.now().plusSeconds(30L*86400)));
    return Map.of("accessToken",tokens.issue(id,role),"refreshToken",refresh,"userId",id,"role",role);
  }
  private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
