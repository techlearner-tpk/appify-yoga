package dev.appify.media;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DailySessionAssetService {
  private final JdbcTemplate db;private final VideoProviderRegistry providers;
  public DailySessionAssetService(JdbcTemplate db,VideoProviderRegistry providers) {this.db=db;this.providers=providers;}
  public UUID ensure(UUID program,LocalDate day,String timezone) {
    ZoneId zone=ZoneId.of(timezone);
    db.update("insert into daily_session_asset(id,program_id,local_date,timezone,access_expires_at) values(?,?,?,?,?) on conflict(program_id,local_date) do nothing",UUID.randomUUID(),program,Date.valueOf(day),timezone,Timestamp.from(day.plusDays(1).atStartOfDay(zone).toInstant()));
    return db.queryForObject("select id from daily_session_asset where program_id=? and local_date=?",UUID.class,program,Date.valueOf(day));
  }
  public void attach(UUID asset,String providerType,String supplied,boolean owned,boolean recordingReady) {
    VideoProvider provider=providers.get(providerType);String normalized=provider.normalizeAssetId(supplied);
    var existing=db.queryForMap("select provider_type,provider_asset_id,asset_status from daily_session_asset where id=? for update",asset);
    String old=(String)existing.get("provider_asset_id");
    if(old!=null && !old.isBlank() && !old.equals(normalized)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Daily asset already has a different video; update it explicitly in asset management");
    String status=recordingReady?"AVAILABLE":"READY_FOR_SOURCE";
    db.update("update daily_session_asset set provider_type=?,provider_asset_id=?,provider_owned=?,asset_status=?,recording_status=?,updated_at=now() where id=?",providerType,normalized,owned,status,recordingReady?"AVAILABLE":"NOT_READY",asset);
    provider.prepareDailyAsset(normalized);
  }
  public void setSourceIfAbsent(UUID asset,UUID slot) {
    db.update("update daily_session_asset set source_session_slot_id=?,updated_at=now() where id=? and (source_session_slot_id is null or (exists(select 1 from session new_slot where new_slot.id=? and new_slot.series_id is not null) and exists(select 1 from session old_slot where old_slot.id=source_session_slot_id and old_slot.series_id is null)))",slot,asset,slot);
  }
}
