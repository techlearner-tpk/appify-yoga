package dev.appify.notifications;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {
  private final JdbcTemplate db; private final String mode;
  public ReminderScheduler(JdbcTemplate db,@Value("${app.mode}") String mode) {this.db=db;this.mode=mode;}
  @Scheduled(initialDelay=20000,fixedDelay=60000) public void reminders() {
    if(!mode.equals("scheduler")) return;
    for(int minutes:new int[]{60,10}) for(Map<String,Object> row:db.queryForList("select e.user_id,s.id as session_id,p.name from session s join program p on p.id=s.program_id join program_enrollment e on e.program_id=p.id where s.status='SCHEDULED' and s.starts_at between now()+ (? * interval '1 minute') and now()+ ((?+1) * interval '1 minute')",minutes,minutes)) {
      UUID session=(UUID)row.get("session_id"),user=(UUID)row.get("user_id");
      String body=row.get("name")+" starts in "+minutes+" minutes. Join: http://localhost:3000/live/"+session;
      db.update("insert into notification(id,user_id,channel,body,dedupe_key) values(?,?,'WHATSAPP',?,?) on conflict(dedupe_key) do nothing",UUID.randomUUID(),user,body,"reminder:"+minutes+":"+session+":"+user);
    }
  }
}
