package dev.appify.scheduling;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/sessions")
public class SessionController {
  private final JdbcTemplate db; public SessionController(JdbcTemplate db) {this.db=db;}
  @GetMapping public List<Map<String,Object>> list(Authentication auth) { return db.queryForList("select s.id,s.program_id,p.name as program_name,s.starts_at,s.ends_at,s.status,s.delivery_type,s.youtube_video_id,i.name as instructor from session s join program p on p.id=s.program_id left join instructor i on i.id=s.instructor_id join program_enrollment e on e.program_id=p.id where e.user_id=? and s.starts_at > now()-interval '1 day' and s.starts_at < now()+interval '14 days' order by s.starts_at limit 100",auth.getPrincipal()); }
  @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id,Authentication auth) {
    var rows=db.queryForList("select s.id,s.program_id,p.name as program_name,s.starts_at,s.ends_at,s.status,s.delivery_type,s.youtube_video_id,i.name as instructor from session s join program p on p.id=s.program_id left join instructor i on i.id=s.instructor_id join program_enrollment e on e.program_id=p.id where s.id=? and e.user_id=?",id,auth.getPrincipal());
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found"); return rows.get(0);
  }
}
