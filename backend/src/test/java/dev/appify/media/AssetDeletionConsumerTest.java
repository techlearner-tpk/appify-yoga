package dev.appify.media;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AssetDeletionConsumerTest {
  @Test void providerFailureSchedulesRetryWithoutLosingOwnershipGuard() throws Exception {
    JdbcTemplate db=mock(JdbcTemplate.class);VideoProvider provider=mock(VideoProvider.class);
    when(provider.type()).thenReturn("TEST");UUID asset=UUID.randomUUID(),event=UUID.randomUUID();
    when(db.update(startsWith("insert into processed_event"),eq(event))).thenReturn(1);
    when(db.queryForList(anyString(),eq(asset))).thenReturn(List.of(Map.of("provider_type","TEST","provider_asset_id","asset-1","provider_owned",true,"deletion_status","QUEUED","delete_attempts",0,"cleanup_enabled",true,"max_delete_attempts",3)));
    doThrow(new IllegalStateException("temporary")).when(provider).deleteAsset("asset-1");
    var consumer=new AssetDeletionConsumer(db,new VideoProviderRegistry(List.of(provider)),new ObjectMapper(),new SimpleMeterRegistry());
    consumer.consume(Map.of("eventId",event.toString(),"eventType","DailyAssetDeleteRequested","payload","{\"assetId\":\""+asset+"\"}"));
    verify(provider).deleteAsset("asset-1");
    verify(db).update(startsWith("update daily_session_asset set deletion_status=?"),eq("PENDING"),eq(1),contains("temporary"),any(Timestamp.class),eq(asset));
  }
  @Test void unownedAssetIsNeverDeleted() throws Exception {
    JdbcTemplate db=mock(JdbcTemplate.class);VideoProvider provider=mock(VideoProvider.class);
    UUID asset=UUID.randomUUID(),event=UUID.randomUUID();
    when(db.update(startsWith("insert into processed_event"),eq(event))).thenReturn(1);
    when(db.queryForList(anyString(),eq(asset))).thenReturn(List.of(Map.of("provider_type","TEST","provider_asset_id","asset-1","provider_owned",false,"deletion_status","QUEUED","delete_attempts",0,"cleanup_enabled",true,"max_delete_attempts",3)));
    new AssetDeletionConsumer(db,new VideoProviderRegistry(List.of(provider)),new ObjectMapper(),new SimpleMeterRegistry()).consume(Map.of("eventId",event.toString(),"eventType","DailyAssetDeleteRequested","payload","{\"assetId\":\""+asset+"\"}"));
    verify(provider,never()).deleteAsset(anyString());
  }
}
