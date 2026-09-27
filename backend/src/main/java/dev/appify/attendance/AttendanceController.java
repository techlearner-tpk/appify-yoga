package dev.appify.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.appify.entitlement.SessionJoinService;
import dev.appify.media.PlaybackService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/attendance")
public class AttendanceController {
  private final JdbcTemplate db; private final ObjectMapper json; private final int minimum; private final SessionJoinService joins; private final PlaybackService playback;
  public AttendanceController(JdbcTemplate db,ObjectMapper json,@Value("${app.attendance-minimum-completion-percent}") int minimum,SessionJoinService joins,PlaybackService playback) {this.db=db;this.json=json;this.minimum=minimum;this.joins=joins;this.playback=playback;}
  public record Request(UUID sessionId,String requestId,String playbackToken) {}
  @PostMapping("/start") @Transactional public Map<String,Object> start(@RequestBody Request body,Authentication auth) {
    if(body.sessionId()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"sessionId required");
    UUID user=(UUID)auth.getPrincipal();
    var access=joins.join(user,body.sessionId());
    var result=new java.util.LinkedHashMap<String,Object>(state(user,body.sessionId()));
    result.put("playbackToken",access.playbackToken());
    return result;
  }
  @PostMapping("/heartbeat") @Transactional public Map<String,Object> heartbeat(@RequestBody Request body,Authentication auth) { return tick(body,auth,false); }
  @PostMapping("/complete") @Transactional public Map<String,Object> complete(@RequestBody Request body,Authentication auth) { return tick(body,auth,true); }
  private Map<String,Object> tick(Request body,Authentication auth,boolean finish) {
    if(body.sessionId()==null || body.requestId()==null || body.requestId().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"sessionId and requestId required");
    UUID user=(UUID)auth.getPrincipal();
    if(body.playbackToken()==null || body.playbackToken().isBlank()) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Playback access required");
    playback.authorizeSlot(user,body.playbackToken(),body.sessionId());
    var rows=db.queryForList("select a.id,a.last_seen_at,a.left_at,a.watched_seconds,a.qualified,s.starts_at,s.ends_at from attendance a join session s on s.id=a.session_id where a.user_id=? and a.session_id=? for update of a",user,body.sessionId());
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attendance not started");
    var row=rows.get(0); UUID attendance=(UUID)row.get("id");
    if(row.get("left_at")!=null) return state(user,body.sessionId());
    int inserted=db.update("insert into attendance_heartbeat(id,attendance_id,request_id,occurred_at) values(?,?,?,?) on conflict(request_id) do nothing",UUID.randomUUID(),attendance,body.requestId(),Timestamp.from(Instant.now()));
    if(inserted==0) return state(user,body.sessionId());
    Instant now=Instant.now(); Instant end=((Timestamp)row.get("ends_at")).toInstant();
    Instant sessionStart=((Timestamp)row.get("starts_at")).toInstant();
    Instant lastSeen=((Timestamp)row.get("last_seen_at")).toInstant();
    long credit=AttendanceRules.credit(lastSeen.isBefore(sessionStart)?sessionStart:lastSeen,now,end);
    long total=((Number)row.get("watched_seconds")).longValue()+credit;
    long scheduled=Duration.between(sessionStart,end).getSeconds();
    boolean qualified=AttendanceRules.qualifies(total,scheduled,minimum);
    db.update("update attendance set last_seen_at=?, watched_seconds=?,qualified=?,left_at=case when ? then ? else left_at end,updated_at=now(),version=version+1 where id=?",Timestamp.from(now),total,qualified,finish,Timestamp.from(now),attendance);
    if(qualified || finish) db.update("update user_daily_participation d set status=case when ? then 'QUALIFIED' else 'COMPLETED' end,qualified_at=case when ? then coalesce(qualified_at,?) else qualified_at end,completed_at=case when ? then coalesce(completed_at,?) else completed_at end,updated_at=now() from session s where s.id=? and d.user_id=? and d.program_id=s.program_id and d.local_date=s.local_date and d.joined_session_slot_id=s.id",qualified,qualified,Timestamp.from(now),finish,Timestamp.from(now),body.sessionId(),user);
    if(qualified && !Boolean.TRUE.equals(row.get("qualified"))) {
      try {
        UUID event=UUID.randomUUID(); String payload=json.writeValueAsString(Map.of("userId",user.toString(),"sessionId",body.sessionId().toString(),"attendanceId",attendance.toString()));
        db.update("insert into outbox_event(id,event_type,aggregate_id,correlation_id,payload) values(?,?,?, ?,?::jsonb)",event,"AttendanceQualified",attendance,body.requestId(),payload);
      } catch(Exception e) {throw new IllegalStateException(e);}
    }
    return state(user,body.sessionId());
  }
  @GetMapping("/{sessionId}") public Map<String,Object> get(@PathVariable UUID sessionId,Authentication auth) { return state((UUID)auth.getPrincipal(),sessionId); }
  private Map<String,Object> state(UUID user,UUID session) {
    List<Map<String,Object>> rows=db.queryForList("select id,watched_seconds,qualified,joined_at,last_seen_at,left_at from attendance where user_id=? and session_id=?",user,session);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attendance not found"); return rows.get(0);
  }
}
