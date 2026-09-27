package dev.appify.notifications;

import dev.appify.entitlement.JoinLinkService;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReminderScheduler {
  private final JdbcTemplate db; private final JoinLinkService links; private final String mode,webOrigin;
  public ReminderScheduler(JdbcTemplate db,JoinLinkService links,@Value("${app.mode}") String mode,@Value("${app.web-origin}") String webOrigin) {this.db=db;this.links=links;this.mode=mode;this.webOrigin=webOrigin;}
  @Scheduled(initialDelay=20000,fixedDelay=60000) @Transactional public void reminders() {
    if(!mode.equals("scheduler")) return;
    for(int minutes:new int[]{60,10}) for(Map<String,Object> row:db.queryForList("select d.user_id,s.id as session_id,p.name from session s join program p on p.id=s.program_id join user_daily_participation d on d.selected_session_slot_id=s.id and d.joined_session_slot_id is null join program_enrollment e on e.program_id=s.program_id and e.user_id=d.user_id and e.active=true where s.status='SCHEDULED' and s.starts_at between now()+ (? * interval '1 minute') and now()+ ((?+1) * interval '1 minute')",minutes,minutes)) {
      UUID session=(UUID)row.get("session_id"),user=(UUID)row.get("user_id");
      String dedupe="reminder:"+minutes+":"+session+":"+user;
      if(db.queryForObject("select count(*) from notification where dedupe_key=?",Integer.class,dedupe)>0) continue;
      String token=links.create(user,session);
      String body=row.get("name")+" starts in "+minutes+" minutes. Join: "+webOrigin+"/j/"+token;
      db.update("insert into notification(id,user_id,channel,body,dedupe_key) values(?,?,'WHATSAPP',?,?) on conflict(dedupe_key) do nothing",UUID.randomUUID(),user,body,dedupe);
    }
  }
}
