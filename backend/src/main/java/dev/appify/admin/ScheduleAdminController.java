package dev.appify.admin;

import java.sql.Date;
import java.sql.Time;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController @RequestMapping("/api/admin/programs/{programId}/schedule")
public class ScheduleAdminController {
  private final JdbcTemplate db;
  public ScheduleAdminController(JdbcTemplate db) {this.db=db;}
  public record SlotInput(UUID id,String localTime,boolean enabled,UUID instructorId) {}
  public record ScheduleInput(String timezone,int durationMinutes,int joinEarlyMinutes,int closeLateMinutes,int cleanupDelayMinutes,boolean cleanupEnabled,int maxDeleteAttempts,int sourceSlotIndex,List<SlotInput> slots) {}

  @GetMapping public Map<String,Object> get(@PathVariable UUID programId) {
    var policy=db.queryForMap("select program_id,timezone,duration_minutes,join_early_minutes,close_late_minutes,cleanup_delay_minutes,cleanup_enabled,max_delete_attempts,source_series_id from program_session_policy where program_id=?",programId);
    return Map.of("policy",policy,"slots",db.queryForList("select id,local_time,active as enabled,instructor_id,sequence_number from class_series where program_id=? order by sequence_number,id",programId));
  }
  @PutMapping @Transactional public Map<String,Object> replace(@PathVariable UUID programId,@RequestBody ScheduleInput input,Authentication auth) {
    if(input.slots()==null || input.slots().isEmpty() || input.slots().size()>12 || input.sourceSlotIndex()<0 || input.sourceSlotIndex()>=input.slots().size()) throw bad("Provide 1 to 12 slots and a valid source slot");
    if(input.durationMinutes()<1 || input.durationMinutes()>360 || input.joinEarlyMinutes()<0 || input.joinEarlyMinutes()>60 || input.closeLateMinutes()<0 || input.closeLateMinutes()>60 || input.cleanupDelayMinutes()<0 || input.cleanupDelayMinutes()>1440 || input.maxDeleteAttempts()<1 || input.maxDeleteAttempts()>20) throw bad("Invalid schedule policy");
    ZoneId zone;try {zone=ZoneId.of(input.timezone());} catch(Exception e) {throw bad("Invalid timezone");}
    if(db.queryForObject("select count(*) from program where id=?",Integer.class,programId)==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Program not found");
    var existing=db.queryForList("select id from class_series where program_id=?",programId);var known=new HashSet<UUID>();for(var row:existing) known.add((UUID)row.get("id"));
    var used=new HashSet<UUID>();var times=new HashSet<LocalTime>();var ids=new ArrayList<UUID>();
    for(int index=0;index<input.slots().size();index++) {
      SlotInput slot=input.slots().get(index);LocalTime time;
      try {time=LocalTime.parse(slot.localTime());} catch(Exception e) {throw bad("Invalid slot time");}
      if(!times.add(time)) throw bad("Slot times must be unique");
      UUID id=slot.id()==null?UUID.randomUUID():slot.id();
      if(slot.id()!=null && !known.contains(id)) throw bad("Slot does not belong to this program");
      if(!used.add(id)) throw bad("Duplicate slot ID");
      ids.add(id);
      if(slot.id()==null) db.update("insert into class_series(id,program_id,instructor_id,local_time,timezone,duration_minutes,sequence_number,active) values(?,?,?,?,?,?,?,?)",id,programId,slot.instructorId(),Time.valueOf(time),zone.toString(),input.durationMinutes(),index+1,slot.enabled());
      else db.update("update class_series set instructor_id=?,local_time=?,timezone=?,duration_minutes=?,sequence_number=?,active=?,updated_at=now() where id=?",slot.instructorId(),Time.valueOf(time),zone.toString(),input.durationMinutes(),index+1,slot.enabled(),id);
    }
    if(!input.slots().get(input.sourceSlotIndex()).enabled()) throw bad("Source slot must be enabled");
    LocalTime sourceTime=LocalTime.parse(input.slots().get(input.sourceSlotIndex()).localTime());
    for(SlotInput slot:input.slots()) if(slot.enabled() && LocalTime.parse(slot.localTime()).isBefore(sourceTime)) throw bad("Source slot must be the earliest enabled time");
    for(UUID id:known) if(!used.contains(id)) db.update("update class_series set active=false,updated_at=now() where id=?",id);
    db.update("update program_session_policy set timezone=?,duration_minutes=?,join_early_minutes=?,close_late_minutes=?,cleanup_delay_minutes=?,cleanup_enabled=?,max_delete_attempts=?,source_series_id=?,updated_at=now() where program_id=?",zone.toString(),input.durationMinutes(),input.joinEarlyMinutes(),input.closeLateMinutes(),input.cleanupDelayMinutes(),input.cleanupEnabled(),input.maxDeleteAttempts(),ids.get(input.sourceSlotIndex()),programId);
    LocalDate tomorrow=LocalDate.now(zone).plusDays(1);
    db.update("update session set status='CANCELLED',updated_at=now() where program_id=? and series_id is not null and local_date>=? and not exists(select 1 from attendance a where a.session_id=session.id)",programId,Date.valueOf(tomorrow));
    db.update("update daily_session_asset set source_session_slot_id=null,updated_at=now() where program_id=? and local_date>=? and not exists(select 1 from user_daily_participation d where d.program_id=? and d.local_date=daily_session_asset.local_date and d.joined_session_slot_id is not null)",programId,Date.valueOf(tomorrow),programId);
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),auth.getPrincipal(),"DAILY_SCHEDULE_UPDATED","program",programId);
    return get(programId);
  }
  private ResponseStatusException bad(String message) {return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
}
