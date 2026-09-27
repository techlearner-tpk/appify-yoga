package dev.appify.admin;

import dev.appify.media.DailySessionAssetService;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin/daily-assets")
public class DailyAssetAdminController {
  private final JdbcTemplate db;private final DailySessionAssetService assets;
  public DailyAssetAdminController(JdbcTemplate db,DailySessionAssetService assets) {this.db=db;this.assets=assets;}
  @GetMapping public List<Map<String,Object>> list(@RequestParam(required=false) UUID programId,@RequestParam(required=false) LocalDate date) {
    return db.queryForList("select a.id,a.program_id,p.name as program_name,a.local_date,a.timezone,a.provider_type,a.provider_asset_id,a.provider_owned,a.asset_status,a.recording_status,a.access_expires_at,a.deletion_status,a.next_delete_attempt_at,a.delete_attempts,a.last_delete_error,a.source_session_slot_id from daily_session_asset a join program p on p.id=a.program_id where (?::uuid is null or a.program_id=?) and (?::date is null or a.local_date=?) order by abs(a.local_date-current_date),a.local_date,p.name limit 200",programId,programId,date==null?null:Date.valueOf(date),date==null?null:Date.valueOf(date));
  }
  public record AttachInput(String providerType,String videoUrl,boolean providerOwned,boolean recordingReady) {}
  @PutMapping("/{id}/provider") @Transactional public Map<String,Object> attach(@PathVariable UUID id,@RequestBody AttachInput input,Authentication auth) {
    if(input.videoUrl()==null || input.videoUrl().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Video URL or ID required");
    var row=db.queryForList("select access_expires_at from daily_session_asset where id=? for update",id);
    if(row.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Daily asset not found");
    if(((java.sql.Timestamp)row.get(0).get("access_expires_at")).toInstant().isBefore(java.time.Instant.now())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Daily asset expired");
    if(db.queryForObject("select count(*) from attendance where session_id in (select id from session where daily_asset_id=?)",Integer.class,id)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot replace an asset after member joins");
    assets.attach(id,input.providerType()==null?"YOUTUBE":input.providerType(),input.videoUrl(),input.providerOwned(),input.recordingReady());
    audit((UUID)auth.getPrincipal(),"DAILY_ASSET_ATTACHED",id);
    return db.queryForMap("select id,asset_status,recording_status,provider_owned from daily_session_asset where id=?",id);
  }
  @PostMapping("/{id}/ready") @Transactional public Map<String,Object> ready(@PathVariable UUID id,Authentication auth) {
    if(db.update("update daily_session_asset set asset_status='AVAILABLE',recording_status='AVAILABLE',updated_at=now() where id=? and provider_asset_id is not null and access_expires_at>now() and asset_status in ('PROCESSING','READY_FOR_SOURCE','SOURCE_ACTIVE')",id)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Asset cannot be marked ready");
    audit((UUID)auth.getPrincipal(),"DAILY_ASSET_READY",id);
    return Map.of("assetStatus","AVAILABLE");
  }
  @PostMapping("/{id}/retry-delete") @Transactional public Map<String,Object> retry(@PathVariable UUID id,Authentication auth) {
    if(db.update("update daily_session_asset set deletion_status='PENDING',next_delete_attempt_at=now(),delete_attempts=0,last_delete_error=null,updated_at=now() where id=? and provider_owned=true and access_expires_at<=now() and deletion_status='FAILED'",id)==0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Deletion cannot be retried");
    audit((UUID)auth.getPrincipal(),"DAILY_ASSET_DELETE_RETRIED",id);
    return Map.of("deletionStatus","PENDING");
  }
  private void audit(UUID actor,String action,UUID asset) {db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),actor,action,"daily_session_asset",asset);}
}
