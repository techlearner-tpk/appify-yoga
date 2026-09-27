package dev.appify.progress;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import dev.appify.scheduling.MemberSessionService;

@RestController
public class ProgressController {
  private final JdbcTemplate db;private final MemberSessionService sessions; public ProgressController(JdbcTemplate db,MemberSessionService sessions) {this.db=db;this.sessions=sessions;}
  @GetMapping("/api/progress") public Map<String,Object> progress(Authentication auth) {
    Object user=auth.getPrincipal();
    var streak=db.queryForMap("select current_days,longest_days,last_qualifying_date,total_qualified_days from streak where user_id=?",user);
    var achievements=db.queryForList("select a.name,a.description,ua.created_at from user_achievement ua join achievement a on a.id=ua.achievement_id where ua.user_id=? order by ua.created_at desc",user);
    var challenges=db.queryForList("select c.name,c.duration_days,ce.progress_days,ce.completed from challenge_enrollment ce join challenge c on c.id=ce.challenge_id where ce.user_id=?",user);
    var weekly=db.queryForObject("select count(distinct (s.starts_at at time zone u.timezone)::date) from attendance a join session s on s.id=a.session_id join app_user u on u.id=a.user_id where a.user_id=? and a.qualified=true and s.starts_at>=now()-interval '7 days'",Integer.class,user);
    return Map.of("streak",streak,"achievements",achievements,"challenges",challenges,"weeklyConsistency",weekly);
  }
  @GetMapping("/api/today") public Map<String,Object> today(Authentication auth) {
    Object user=auth.getPrincipal();
    var profile=db.queryForMap("select display_name,timezone from app_user where id=?",user);
    var todaySessions=sessions.today((java.util.UUID)user);
    var habits=db.queryForList("select h.id,h.name,exists(select 1 from habit_log l where l.habit_id=h.id and l.user_id=? and l.logged_date=(now() at time zone u.timezone)::date) as done from habit_definition h cross join app_user u where u.id=? and h.active=true",user,user);
    return Map.of("profile",profile,"sessions",todaySessions,"habits",habits,"progress",progress(auth));
  }
  @PostMapping("/api/habits/{id}/complete") public Map<String,Object> completeHabit(@PathVariable java.util.UUID id,Authentication auth) {
    db.update("insert into habit_log(id,user_id,habit_id,logged_date) select ?,u.id,?,(now() at time zone u.timezone)::date from app_user u where u.id=? on conflict(user_id,habit_id,logged_date) do nothing",java.util.UUID.randomUUID(),id,auth.getPrincipal()); return Map.of("completed",true);
  }
}
