package dev.appify.programs;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/programs")
public class ProgramController {
  private final JdbcTemplate db; public ProgramController(JdbcTemplate db) {this.db=db;}
  @GetMapping public List<Map<String,Object>> list() { return db.queryForList("select id,name,description,difficulty,duration_minutes,program_type,image_url,active from program where active=true order by name"); }
  @PostMapping("/{id}/enroll") public Map<String,Object> enroll(@PathVariable UUID id,Authentication auth) {
    if(db.queryForObject("select count(*) from program where id=? and active=true",Integer.class,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Program not found");
    db.update("insert into program_enrollment(id,user_id,program_id) values(?,?,?) on conflict(user_id,program_id) do nothing",UUID.randomUUID(),auth.getPrincipal(),id);
    return Map.of("enrolled",true);
  }
  @GetMapping("/enrolled") public List<Map<String,Object>> enrolled(Authentication auth) {return db.queryForList("select p.id,p.name,p.description from program p join program_enrollment e on e.program_id=p.id where e.user_id=?",auth.getPrincipal());}
}
