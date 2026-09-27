package dev.appify.admin;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin/instructors")
public class InstructorAdminController {
  private final JdbcTemplate db;
  public InstructorAdminController(JdbcTemplate db) {this.db=db;}

  @GetMapping public List<Map<String,Object>> list() {
    return db.queryForList("select i.id,i.name,i.bio,u.email from instructor i join app_user u on u.id=i.user_id order by i.name");
  }

  public record Onboard(String email,String bio) {}
  @PostMapping @Transactional public Map<String,Object> onboard(@RequestBody Onboard input,Authentication auth) {
    if(input.email()==null || input.email().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email required");
    var users=db.queryForList("select id,display_name,role from app_user where email=? and disabled=false for update",input.email().trim().toLowerCase());
    if(users.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Ask the instructor to register first");
    var user=users.get(0);
    if(!"USER".equals(user.get("role"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Account already has a staff role");
    UUID userId=(UUID)user.get("id"),instructorId=UUID.randomUUID();
    db.update("insert into instructor(id,user_id,name,bio) values(?,?,?,?)",instructorId,userId,user.get("display_name"),input.bio()==null?"":input.bio().trim());
    db.update("update app_user set role='INSTRUCTOR',updated_at=now(),version=version+1 where id=?",userId);
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),auth.getPrincipal(),"INSTRUCTOR_ONBOARDED","instructor",instructorId);
    return Map.of("id",instructorId,"email",input.email().trim().toLowerCase());
  }
}
