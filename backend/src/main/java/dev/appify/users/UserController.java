package dev.appify.users;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/me")
public class UserController {
  private final JdbcTemplate db; public UserController(JdbcTemplate db) {this.db=db;}
  @GetMapping public Map<String,Object> me(Authentication auth) { return db.queryForMap("select id,email,display_name,role,timezone,language,wellness_goal,referral_code from app_user where id=?",auth.getPrincipal()); }
  public record Update(String displayName,String timezone,String language,String wellnessGoal,String preferredTime) {}
  @PutMapping public Map<String,Object> update(Authentication auth,@RequestBody Update input) {
    if(input.timezone()!=null) try { java.time.ZoneId.of(input.timezone()); } catch(Exception e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid timezone");}
    db.update("update app_user set display_name=coalesce(?,display_name), timezone=coalesce(?,timezone),language=coalesce(?,language),wellness_goal=coalesce(?,wellness_goal),preferred_time=coalesce(?,preferred_time),updated_at=now(),version=version+1 where id=?",input.displayName(),input.timezone(),input.language(),input.wellnessGoal(),input.preferredTime(),auth.getPrincipal());
    return me(auth);
  }
}
