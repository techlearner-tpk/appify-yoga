package dev.appify.instructor;

import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/instructor")
public class InstructorController {
  private final JdbcTemplate db; public InstructorController(JdbcTemplate db) {this.db=db;}
  @GetMapping("/sessions") public List<Map<String,Object>> sessions(Authentication auth) {
    boolean admin=auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return db.queryForList("select s.id,p.name as program,s.starts_at,s.ends_at,s.youtube_video_id,count(a.id) as joined_users,count(a.id) filter(where a.qualified) as qualified_users from session s join program p on p.id=s.program_id left join attendance a on a.session_id=s.id where s.starts_at>now()-interval '1 day' and (? or s.instructor_id in (select id from instructor where user_id=?)) group by s.id,p.name order by s.starts_at limit 100",admin,auth.getPrincipal());
  }
}
