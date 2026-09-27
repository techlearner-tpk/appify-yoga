package dev.appify.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.appify.events.MessagingConfig;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component @Profile("worker")
public class AssetDeletionConsumer {
  private final JdbcTemplate db;private final VideoProviderRegistry providers;private final ObjectMapper json;private final MeterRegistry metrics;
  public AssetDeletionConsumer(JdbcTemplate db,VideoProviderRegistry providers,ObjectMapper json,MeterRegistry metrics) {this.db=db;this.providers=providers;this.json=json;this.metrics=metrics;}
  @RabbitListener(queues=MessagingConfig.ASSET_QUEUE) @Transactional public void consume(Map<String,String> event) throws Exception {
    if(!"DailyAssetDeleteRequested".equals(event.get("eventType"))) return;
    UUID eventId=UUID.fromString(event.get("eventId")),id=UUID.fromString(json.readTree(event.get("payload")).path("assetId").asText());
    if(db.update("insert into processed_event(consumer,event_id) values('asset-deletion',?) on conflict do nothing",eventId)==0) return;
    var rows=db.queryForList("select a.provider_type,a.provider_asset_id,a.provider_owned,a.deletion_status,a.delete_attempts,p.cleanup_enabled,p.max_delete_attempts from daily_session_asset a join program_session_policy p on p.program_id=a.program_id where a.id=? for update of a",id);
    if(rows.isEmpty()) return;var row=rows.get(0);
    if(!"QUEUED".equals(row.get("deletion_status")) || !Boolean.TRUE.equals(row.get("provider_owned")) || !Boolean.TRUE.equals(row.get("cleanup_enabled"))) return;
    int attempt=((Number)row.get("delete_attempts")).intValue()+1;
    try {
      providers.get((String)row.get("provider_type")).deleteAsset((String)row.get("provider_asset_id"));
      db.update("update daily_session_asset set deletion_status='DELETED',deleted_at=now(),delete_attempts=?,last_delete_error=null,updated_at=now() where id=?",attempt,id);
      metrics.counter("daily_asset_delete_success").increment();
    } catch(Exception e) {
      boolean failed=attempt>=((Number)row.get("max_delete_attempts")).intValue();
      long delay=Math.min(3600,60L<<Math.min(attempt-1,6));
      db.update("update daily_session_asset set deletion_status=?,delete_attempts=?,last_delete_error=?,next_delete_attempt_at=?,updated_at=now() where id=?",failed?"FAILED":"PENDING",attempt,e.getClass().getSimpleName()+": "+String.valueOf(e.getMessage()).substring(0,Math.min(300,String.valueOf(e.getMessage()).length())),Timestamp.from(Instant.now().plusSeconds(delay)),id);
      metrics.counter("daily_asset_delete_failure").increment();
    }
  }
}
