package dev.appify.events;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublisher {
  private static final Logger log=LoggerFactory.getLogger(OutboxPublisher.class);
  private final JdbcTemplate db; private final EventQueue queue; private final String mode;
  public OutboxPublisher(JdbcTemplate db,EventQueue queue,@Value("${app.mode}") String mode) {this.db=db;this.queue=queue;this.mode=mode;}
  @Scheduled(initialDelay=5000,fixedDelay=2000) public void publish() {
    if(!mode.equals("worker")) return;
    var rows=db.queryForList("select id,event_type,event_version,aggregate_id,correlation_id,payload::text as payload,created_at from outbox_event where published_at is null order by created_at limit 100");
    for(Map<String,Object> row:rows) {
      UUID id=(UUID)row.get("id");
      try {
        queue.publish(id,(String)row.get("event_type"),(String)row.get("payload"));
        db.update("update outbox_event set published_at=now(),attempts=attempts+1 where id=? and published_at is null",id);
      } catch(Exception e) { db.update("update outbox_event set attempts=attempts+1 where id=?",id);log.error("outbox_publish_failed eventId={}",id,e);break; }
    }
  }
}
