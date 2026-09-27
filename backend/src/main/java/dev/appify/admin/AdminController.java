package dev.appify.admin;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import dev.appify.events.MessagingConfig;

@RestController @RequestMapping("/api/admin")
public class AdminController {
  private final JdbcTemplate db; private final RabbitAdmin rabbit;
  public AdminController(JdbcTemplate db,org.springframework.amqp.rabbit.connection.ConnectionFactory cf) {this.db=db;this.rabbit=new RabbitAdmin(cf);}
  @GetMapping("/dashboard") public Map<String,Object> dashboard() {
    Integer queue=null; try {var props=rabbit.getQueueProperties(MessagingConfig.QUEUE); if(props!=null) queue=(Integer)props.get(RabbitAdmin.QUEUE_MESSAGE_COUNT);} catch(Exception ignored) {}
    return Map.of("users",db.queryForObject("select count(*) from app_user",Integer.class),"activeUsers",db.queryForObject("select count(distinct user_id) from attendance where joined_at>now()-interval '30 days'",Integer.class),"todaySessions",db.queryForObject("select count(*) from session where starts_at::date=current_date",Integer.class),"todayAttendance",db.queryForObject("select count(*) from attendance where joined_at::date=current_date",Integer.class),"qualifiedAttendance",db.queryForObject("select count(*) from attendance where qualified=true",Integer.class),"failedMessages",db.queryForObject("select count(*) from notification where status='FAILED'",Integer.class),"queueDepth",queue==null?-1:queue);
  }
  @GetMapping("/users") public List<Map<String,Object>> users() {return db.queryForList("select id,email,display_name,role,disabled,created_at from app_user order by created_at desc limit 200");}
  @GetMapping("/programs") public List<Map<String,Object>> programs() {return db.queryForList("select * from program order by created_at desc");}
  public record ProgramInput(String name,String description,String difficulty,int durationMinutes,String programType) {}
  @PostMapping("/programs") public Map<String,Object> createProgram(@RequestBody ProgramInput p,Authentication auth) {
    if(p.name()==null || p.name().isBlank() || p.durationMinutes()<1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Name and duration required");
    UUID id=UUID.randomUUID();db.update("insert into program(id,name,description,difficulty,duration_minutes,program_type) values(?,?,?,?,?,?)",id,p.name(),p.description()==null?"":p.description(),p.difficulty()==null?"BEGINNER":p.difficulty(),p.durationMinutes(),p.programType()==null?"YOGA":p.programType());audit((UUID)auth.getPrincipal(),"PROGRAM_CREATED","program",id);return Map.of("id",id);
  }
  public record ScheduleInput(UUID programId,String localTime,String timezone,int durationMinutes,String youtubeVideoId) {}
  @PostMapping("/schedules") public Map<String,Object> schedule(@RequestBody ScheduleInput p,Authentication auth) {
    UUID id=UUID.randomUUID(); db.update("insert into class_series(id,program_id,local_time,timezone,duration_minutes,youtube_video_id) values(?,?,?::time,?,?,?)",id,p.programId(),p.localTime(),p.timezone(),p.durationMinutes(),p.youtubeVideoId());audit((UUID)auth.getPrincipal(),"SCHEDULE_CHANGED","class_series",id);return Map.of("id",id);
  }
  @GetMapping("/schedules") public List<Map<String,Object>> schedules() {return db.queryForList("select * from class_series order by local_time");}
  public record SessionInput(UUID programId,Instant startsAt,int durationMinutes,String youtubeVideoId) {}
  @PostMapping("/sessions") public Map<String,Object> session(@RequestBody SessionInput p,Authentication auth) {
    UUID id=UUID.randomUUID();db.update("insert into session(id,program_id,starts_at,ends_at,timezone,youtube_video_id,status) values(?,?,?,?,? ,?,'LIVE')",id,p.programId(),Timestamp.from(p.startsAt()),Timestamp.from(p.startsAt().plusSeconds(p.durationMinutes()*60L)),"Asia/Kolkata",p.youtubeVideoId());audit((UUID)auth.getPrincipal(),"SESSION_CREATED","session",id);return Map.of("id",id);
  }
  @GetMapping("/sessions") public List<Map<String,Object>> sessions() {return db.queryForList("select * from session order by starts_at desc limit 200");}
  @GetMapping("/attendance") public List<Map<String,Object>> attendance() {return db.queryForList("select a.*,u.email from attendance a join app_user u on u.id=a.user_id order by a.created_at desc limit 200");}
  @GetMapping("/notifications") public List<Map<String,Object>> notifications() {return db.queryForList("select n.id,u.email,n.channel,n.body,n.status,n.created_at from notification n join app_user u on u.id=n.user_id order by n.created_at desc limit 200");}
  @GetMapping("/fake-whatsapp") public List<Map<String,Object>> fakeWhatsApp() {return db.queryForList("select notification_id,destination,body,status,created_at from fake_whatsapp_message order by created_at desc limit 200");}
  @GetMapping("/audit") public List<Map<String,Object>> audit() {return db.queryForList("select * from audit_log order by created_at desc limit 200");}
  @GetMapping("/outbox") public List<Map<String,Object>> outbox() {return db.queryForList("select id,event_type,attempts,published_at,created_at from outbox_event order by created_at desc limit 200");}
  @GetMapping("/health") public Map<String,Object> health() {return Map.of("database",db.queryForObject("select 1",Integer.class),"outboxPending",db.queryForObject("select count(*) from outbox_event where published_at is null",Integer.class));}
  private void audit(UUID actor,String action,String type,UUID id) {db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),actor,action,type,id);}
}
