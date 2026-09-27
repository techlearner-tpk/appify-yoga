package dev.appify.media;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AssetLifecycleScheduler {
  private final JdbcTemplate db;private final VideoProviderRegistry providers;private final ObjectMapper json;private final MeterRegistry metrics;private final String mode;
  public AssetLifecycleScheduler(JdbcTemplate db,VideoProviderRegistry providers,ObjectMapper json,MeterRegistry metrics,@Value("${app.mode}") String mode) {this.db=db;this.providers=providers;this.json=json;this.metrics=metrics;this.mode=mode;}

  @Scheduled(initialDelay=20000,fixedDelay=60000) @Transactional public void advance() {
    if(!"scheduler".equals(mode)) return;
    for(var a:db.queryForList("select a.id,a.provider_type,a.provider_asset_id,a.asset_status,s.starts_at,s.ends_at from daily_session_asset a join session s on s.id=a.source_session_slot_id where a.provider_asset_id is not null and a.asset_status in ('READY_FOR_SOURCE','SOURCE_ACTIVE','PROCESSING') order by a.local_date limit 200")) {
      UUID id=(UUID)a.get("id");String status=(String)a.get("asset_status"),providerType=(String)a.get("provider_type"),assetId=(String)a.get("provider_asset_id");
      Instant start=((Timestamp)a.get("starts_at")).toInstant(),end=((Timestamp)a.get("ends_at")).toInstant(),now=Instant.now();
      if("READY_FOR_SOURCE".equals(status) && !now.isBefore(start) && now.isBefore(end)) {
        providers.get(providerType).startSourceSession(assetId);
        db.update("update daily_session_asset set asset_status='SOURCE_ACTIVE',updated_at=now() where id=? and asset_status='READY_FOR_SOURCE'",id);
      } else if("READY_FOR_SOURCE".equals(status) && !now.isBefore(end)) {
        db.update("update daily_session_asset set asset_status='PROCESSING',recording_status='PROCESSING',updated_at=now() where id=? and asset_status='READY_FOR_SOURCE'",id);
      } else if("SOURCE_ACTIVE".equals(status) && !now.isBefore(end)) {
        providers.get(providerType).endSourceSession(assetId);
        db.update("update daily_session_asset set asset_status='PROCESSING',recording_status='PROCESSING',updated_at=now() where id=? and asset_status='SOURCE_ACTIVE'",id);
      } else if("PROCESSING".equals(status)) {
        var readiness=providers.get(providerType).checkAssetReadiness(assetId);
        if(readiness==VideoProvider.Readiness.AVAILABLE) {db.update("update daily_session_asset set asset_status='AVAILABLE',recording_status='AVAILABLE',updated_at=now() where id=? and asset_status='PROCESSING'",id);metrics.counter("daily_asset_ready").increment();}
        if(readiness==VideoProvider.Readiness.FAILED) db.update("update daily_session_asset set asset_status='FAILED',recording_status='FAILED',updated_at=now() where id=? and asset_status='PROCESSING'",id);
      }
    }
    db.update("update playback_session set status='EXPIRED' where status='ACTIVE' and expires_at<=now()");
    for(var a:db.queryForList("select a.id,a.provider_asset_id,a.provider_owned,p.cleanup_enabled,p.cleanup_delay_minutes from daily_session_asset a join program_session_policy p on p.program_id=a.program_id where a.access_expires_at<=now() and a.asset_status<>'ACCESS_EXPIRED' limit 200")) {
      UUID id=(UUID)a.get("id");
      db.update("update daily_session_asset set asset_status='ACCESS_EXPIRED',updated_at=now() where id=?",id);
      db.update("update playback_session set status='EXPIRED' where daily_session_asset_id=? and status='ACTIVE'",id);
      db.update("update session_join_link set revoked=true where session_slot_id in (select id from session where daily_asset_id=?)",id);
      if(a.get("provider_asset_id")!=null && Boolean.TRUE.equals(a.get("provider_owned")) && Boolean.TRUE.equals(a.get("cleanup_enabled"))) db.update("update daily_session_asset set deletion_status='PENDING',next_delete_attempt_at=access_expires_at+(? * interval '1 minute') where id=? and deletion_status='NONE'",a.get("cleanup_delay_minutes"),id);
    }
    db.update("update daily_session_asset a set deletion_status='PENDING',next_delete_attempt_at=a.access_expires_at+(p.cleanup_delay_minutes * interval '1 minute'),updated_at=now() from program_session_policy p where p.program_id=a.program_id and a.asset_status='ACCESS_EXPIRED' and a.provider_owned=true and p.cleanup_enabled=true and a.provider_asset_id is not null and a.deletion_status='NONE'");
    for(var a:db.queryForList("select a.id from daily_session_asset a join program_session_policy p on p.program_id=a.program_id where a.deletion_status='PENDING' and a.next_delete_attempt_at<=now() and a.delete_attempts<p.max_delete_attempts limit 100")) {
      UUID id=(UUID)a.get("id");
      if(db.update("update daily_session_asset set deletion_status='QUEUED',deletion_requested_at=now(),updated_at=now() where id=? and deletion_status='PENDING'",id)==0) continue;
      try {db.update("insert into outbox_event(id,event_type,aggregate_id,correlation_id,payload) values(?,'DailyAssetDeleteRequested',?,?::text,?::jsonb)",UUID.randomUUID(),id,"asset-delete:"+id+":"+Instant.now().toEpochMilli(),json.writeValueAsString(Map.of("assetId",id.toString())));metrics.counter("daily_asset_delete_queued").increment();}
      catch(Exception e) {throw new IllegalStateException(e);}
    }
  }
}
