package dev.appify.instructor;

import dev.appify.media.VideoProviderRegistry;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController @RequestMapping("/api/instructor")
public class InstructorController {
  private final JdbcTemplate db;private final VideoProviderRegistry providers; public InstructorController(JdbcTemplate db,VideoProviderRegistry providers) {this.db=db;this.providers=providers;}
  @GetMapping("/sessions") public List<Map<String,Object>> sessions(Authentication auth) {
    boolean admin=auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    return db.queryForList("select s.id,p.name as program,s.starts_at,s.ends_at,coalesce(s.id=d.source_session_slot_id and d.asset_status='READY_FOR_SOURCE' and s.starts_at<=now()+interval '5 minutes' and s.ends_at>now(),false) as can_start_source,count(a.id) as joined_users,count(a.id) filter(where a.qualified) as qualified_users from session s join program p on p.id=s.program_id left join daily_session_asset d on d.id=s.daily_asset_id left join attendance a on a.session_id=s.id where s.starts_at>now()-interval '1 day' and (? or s.instructor_id in (select id from instructor where user_id=?)) group by s.id,p.name,d.source_session_slot_id,d.asset_status order by s.starts_at limit 100",admin,auth.getPrincipal());
  }
  @PostMapping("/sessions/{id}/start") @Transactional public Map<String,Object> start(@PathVariable UUID id,Authentication auth) {
    boolean admin=auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    var rows=db.queryForList("select d.id as asset_id,d.provider_type,d.provider_asset_id,d.asset_status from session s join daily_session_asset d on d.id=s.daily_asset_id where s.id=? and d.source_session_slot_id=s.id and s.starts_at<=now()+interval '5 minutes' and s.ends_at>now() and (? or s.instructor_id in (select id from instructor where user_id=?)) for update of d",id,admin,auth.getPrincipal());
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Source class is not assigned or not open");
    var row=rows.get(0);if("SOURCE_ACTIVE".equals(row.get("asset_status"))) return Map.of("started",true);
    if(!"READY_FOR_SOURCE".equals(row.get("asset_status")) || row.get("provider_asset_id")==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Source media is not ready");
    providers.get((String)row.get("provider_type")).startSourceSession((String)row.get("provider_asset_id"));
    db.update("update daily_session_asset set asset_status='SOURCE_ACTIVE',updated_at=now() where id=?",row.get("asset_id"));
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),auth.getPrincipal(),"SOURCE_SESSION_STARTED","session",id);
    return Map.of("started",true);
  }
}
