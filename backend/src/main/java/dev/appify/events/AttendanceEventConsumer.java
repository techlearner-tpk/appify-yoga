package dev.appify.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("worker")
public class AttendanceEventConsumer {
  private final JdbcTemplate db; private final ObjectMapper json;
  public AttendanceEventConsumer(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}
  @RabbitListener(queues=MessagingConfig.QUEUE)
  @Transactional public void consume(Map<String,String> event) throws Exception {
    UUID id=UUID.fromString(event.get("eventId"));
    int first=db.update("insert into processed_event(consumer,event_id) values('attendance',?) on conflict do nothing",id);
    if(first==0 || !"AttendanceQualified".equals(event.get("eventType"))) return;
    JsonNode payload=json.readTree(event.get("payload")); UUID user=UUID.fromString(payload.get("userId").asText()); UUID attendance=UUID.fromString(payload.get("attendanceId").asText());
    var rows=db.queryForList("select (s.starts_at at time zone u.timezone)::date as day,s.program_id from attendance a join session s on s.id=a.session_id join app_user u on u.id=a.user_id where a.id=? and a.qualified=true",attendance);
    if(rows.isEmpty()) return;
    LocalDate day=((java.sql.Date)rows.get(0).get("day")).toLocalDate(); UUID program=(UUID)rows.get(0).get("program_id");
    int inserted=db.update("insert into qualified_day(user_id,qualified_date,first_attendance_id) values(?,?,?) on conflict do nothing",user,java.sql.Date.valueOf(day),attendance);
    if(inserted==0) return;
    var dates=db.query("select qualified_date from qualified_day where user_id=? order by qualified_date desc",(rs,n)->rs.getDate(1).toLocalDate(),user);
    int longest=0,run=0; LocalDate previous=null;
    for(int i=dates.size()-1;i>=0;i--) {LocalDate current=dates.get(i);run=(previous!=null && previous.plusDays(1).equals(current))?run+1:1;longest=Math.max(longest,run);previous=current;}
    int current=0; LocalDate expected=dates.get(0);
    if(expected.equals(LocalDate.now()) || expected.equals(LocalDate.now().minusDays(1))) for(LocalDate d:dates) {if(!d.equals(expected)) break;current++;expected=expected.minusDays(1);}
    db.update("update streak set current_days=?,longest_days=?,last_qualifying_date=?,total_qualified_days=?,updated_at=now(),version=version+1 where user_id=?",current,longest,java.sql.Date.valueOf(dates.get(0)),dates.size(),user);
    db.update("update challenge_enrollment ce set progress_days=(select count(distinct (s.starts_at at time zone u.timezone)::date) from attendance a join session s on s.id=a.session_id join app_user u on u.id=a.user_id join challenge c on c.id=ce.challenge_id where a.user_id=ce.user_id and a.qualified=true and a.created_at>=ce.created_at and (c.required_program_id is null or c.required_program_id=s.program_id)), updated_at=now() where ce.user_id=?",user);
    db.update("update challenge_enrollment ce set completed=true,updated_at=now() from challenge c where ce.challenge_id=c.id and ce.user_id=? and ce.progress_days>=c.duration_days and ce.completed=false",user);
    int sessions=db.queryForObject("select count(*) from attendance where user_id=? and qualified=true",Integer.class,user);
    db.update("insert into user_achievement(id,user_id,achievement_id) select gen_random_uuid(),?,id from achievement where (code='FIRST_SESSION' and ? >= 1) or (code='THREE_DAY' and ? >= 3) or (code='SEVEN_DAY' and ? >= 7) or (code='TWENTY_ONE_DAY' and ? >= 21) or (code='THIRTY_DAY' and ? >= 30) or (code='HUNDRED_SESSIONS' and ? >= 100) on conflict(user_id,achievement_id) do nothing",user,sessions,current,current,current,current,sessions);
    String body="Great work! Your class counted today. Current streak: "+current+" day"+(current==1?"":"s")+".";
    db.update("insert into notification(id,user_id,channel,body,dedupe_key) values(?,?,'WHATSAPP',?,?) on conflict(dedupe_key) do nothing",UUID.randomUUID(),user,body,"qualified:"+attendance);
  }
}
