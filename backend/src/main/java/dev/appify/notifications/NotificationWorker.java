package dev.appify.notifications;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationWorker {
  private static final Logger log=LoggerFactory.getLogger(NotificationWorker.class);
  private final JdbcTemplate db; private final NotificationProvider provider; private final String mode;
  public NotificationWorker(JdbcTemplate db,NotificationProvider provider,@Value("${app.mode}") String mode) {this.db=db;this.provider=provider;this.mode=mode;}
  @Scheduled(initialDelay=10000,fixedDelay=5000) public void process() {
    if(!mode.equals("worker")) return;
    for(Map<String,Object> n:db.queryForList("select n.id,n.body,u.email from notification n join app_user u on u.id=n.user_id where n.status='PENDING' or (n.status='SENDING' and n.updated_at<now()-interval '5 minutes') order by n.created_at limit 100")) {
      UUID id=(UUID)n.get("id");
      if(db.update("update notification set status='SENDING',updated_at=now() where id=? and (status='PENDING' or (status='SENDING' and updated_at<now()-interval '5 minutes'))",id)==0) continue;
      try {provider.send(id,(String)n.get("email"),(String)n.get("body"));db.update("update notification set status='DELIVERED',updated_at=now() where id=?",id);db.update("insert into notification_delivery(id,notification_id,status) values(?,?,'DELIVERED')",UUID.randomUUID(),id);}
      catch(Exception e) {int attempts=db.queryForObject("select count(*) from notification_delivery where notification_id=? and status='FAILED'",Integer.class,id)+1;db.update("update notification set status=?,updated_at=now() where id=?",attempts>=3?"FAILED":"PENDING",id);db.update("insert into notification_delivery(id,notification_id,status,error) values(?,?,'FAILED',?)",UUID.randomUUID(),id,e.getMessage());log.warn("notification_failed id={} attempt={}",id,attempts);}
    }
  }
}
