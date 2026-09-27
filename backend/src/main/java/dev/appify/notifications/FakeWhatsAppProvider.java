package dev.appify.notifications;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class FakeWhatsAppProvider implements NotificationProvider {
  private static final Logger log=LoggerFactory.getLogger(FakeWhatsAppProvider.class);
  private final double failureRate;
  private final JdbcTemplate db;
  public FakeWhatsAppProvider(@Value("${app.fake-whatsapp-failure-rate}") double failureRate,JdbcTemplate db) {this.failureRate=failureRate;this.db=db;}
  @Override public void send(UUID id,String destination,String body) {
    if(db.queryForObject("select count(*) from fake_whatsapp_message where notification_id=?",Integer.class,id)>0) return;
    if(ThreadLocalRandom.current().nextDouble()<failureRate) throw new IllegalStateException("Simulated WhatsApp failure");
    if(db.update("insert into fake_whatsapp_message(notification_id,destination,body,status) values(?,?,?,'DELIVERED') on conflict do nothing",id,destination,body)>0)
      log.info("fake_whatsapp_delivered notificationId={}",id);
  }
}
