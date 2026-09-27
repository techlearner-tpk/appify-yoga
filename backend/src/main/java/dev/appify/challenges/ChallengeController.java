package dev.appify.challenges;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/challenges")
public class ChallengeController {
  private final JdbcTemplate db; public ChallengeController(JdbcTemplate db) {this.db=db;}
  @GetMapping public List<Map<String,Object>> list() {return db.queryForList("select id,name,duration_days,reward,active from challenge where active=true order by duration_days");}
  @PostMapping("/{id}/enroll") public Map<String,Object> enroll(@PathVariable UUID id,Authentication auth) {
    if(db.queryForObject("select count(*) from challenge where id=? and active=true",Integer.class,id)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Challenge not found");
    db.update("insert into challenge_enrollment(id,user_id,challenge_id) values(?,?,?) on conflict(user_id,challenge_id) do nothing",UUID.randomUUID(),auth.getPrincipal(),id); return Map.of("enrolled",true);
  }
}
