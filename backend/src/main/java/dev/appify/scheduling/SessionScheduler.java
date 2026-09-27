package dev.appify.scheduling;

import dev.appify.media.DailySessionAssetService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionScheduler {
  private final JdbcTemplate db;private final DailySessionAssetService assets;private final String mode;
  public SessionScheduler(JdbcTemplate db,DailySessionAssetService assets,@Value("${app.mode}") String mode) {this.db=db;this.assets=assets;this.mode=mode;}
  @Scheduled(initialDelay=10000,fixedDelay=60000) public void materialize() {
    if(!mode.equals("scheduler")) return;
    var series=db.queryForList("select c.id,c.program_id,c.instructor_id,c.local_time,c.timezone,c.days_of_week,c.duration_minutes,c.sequence_number,p.source_series_id from class_series c join program_session_policy p on p.program_id=c.program_id where c.active=true order by c.program_id,c.sequence_number,c.id");
    for(var row:series) {
      UUID seriesId=(UUID)row.get("id"),program=(UUID)row.get("program_id");
      ZoneId zone=ZoneId.of((String)row.get("timezone"));LocalTime time=((java.sql.Time)row.get("local_time")).toLocalTime();
      String days=(String)row.get("days_of_week");int duration=((Number)row.get("duration_minutes")).intValue();
      for(int i=0;i<14;i++) {
        LocalDate day=LocalDate.now(zone).plusDays(i);
        if(!days.contains(day.getDayOfWeek().name().substring(0,3))) continue;
        UUID asset=assets.ensure(program,day,zone.toString());
        Instant start=day.atTime(time).atZone(zone).toInstant();UUID slot=UUID.randomUUID();
        db.update("insert into session(id,series_id,program_id,instructor_id,starts_at,ends_at,timezone,local_date,daily_asset_id,sequence_number) values(?,?,?,?,?,?,?,?,?,?) on conflict(series_id,starts_at) do update set status='SCHEDULED',instructor_id=excluded.instructor_id,ends_at=excluded.ends_at,timezone=excluded.timezone,local_date=excluded.local_date,daily_asset_id=excluded.daily_asset_id,sequence_number=excluded.sequence_number,updated_at=now() where session.status='CANCELLED'",slot,seriesId,program,row.get("instructor_id"),Timestamp.from(start),Timestamp.from(start.plusSeconds(duration*60L)),zone.toString(),Date.valueOf(day),asset,row.get("sequence_number"));
        UUID actual=db.queryForObject("select id from session where series_id=? and starts_at=?",UUID.class,seriesId,Timestamp.from(start));
        UUID configuredSource=(UUID)row.get("source_series_id");
        if(seriesId.equals(configuredSource) || (configuredSource==null && ((Number)row.get("sequence_number")).intValue()==1)) assets.setSourceIfAbsent(asset,actual);
      }
    }
  }
  @Scheduled(initialDelay=15000,fixedDelay=30000) public void updateStatuses() {
    if(!mode.equals("scheduler")) return;
    db.update("update session set status='LIVE',updated_at=now(),version=version+1 where status='SCHEDULED' and starts_at<=now() and ends_at>now()");
    db.update("update session set status='COMPLETED',updated_at=now(),version=version+1 where status in ('SCHEDULED','LIVE') and ends_at<=now()");
  }
}
