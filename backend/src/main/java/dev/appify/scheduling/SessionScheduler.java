package dev.appify.scheduling;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionScheduler {
  private final JdbcTemplate db; private final String mode; private final String defaultVideo;
  public SessionScheduler(JdbcTemplate db,@Value("${app.mode}") String mode,@Value("${app.youtube-video-id}") String video) {this.db=db;this.mode=mode;this.defaultVideo=video;}
  @Scheduled(initialDelay=10000,fixedDelay=3600000) public void materialize() {
    if(!mode.equals("scheduler")) return;
    db.query("select id,program_id,instructor_id,local_time,timezone,days_of_week,duration_minutes,youtube_video_id from class_series where active=true",rs -> {
      UUID series=(UUID)rs.getObject("id"); ZoneId zone=ZoneId.of(rs.getString("timezone")); LocalTime time=rs.getTime("local_time").toLocalTime();
      for(int i=0;i<14;i++) { LocalDate day=LocalDate.now(zone).plusDays(i); if(!rs.getString("days_of_week").contains(day.getDayOfWeek().name().substring(0,3))) continue;
        Instant start=day.atTime(time).atZone(zone).toInstant(); String video=rs.getString("youtube_video_id"); if(video==null || video.isBlank()) video=defaultVideo;
        db.update("insert into session(id,series_id,program_id,instructor_id,starts_at,ends_at,timezone,youtube_video_id) values(?,?,?,?,?,?,?,?) on conflict(series_id,starts_at) do nothing",UUID.randomUUID(),series,rs.getObject("program_id"),rs.getObject("instructor_id"),Timestamp.from(start),Timestamp.from(start.plusSeconds(rs.getInt("duration_minutes")*60L)),zone.toString(),video);
      }
    });
  }
  @Scheduled(initialDelay=15000,fixedDelay=30000) public void updateStatuses() {
    if(!mode.equals("scheduler")) return;
    db.update("update session set status='LIVE',updated_at=now(),version=version+1 where status='SCHEDULED' and starts_at<=now() and ends_at>now()");
    db.update("update session set status='COMPLETED',updated_at=now(),version=version+1 where status in ('SCHEDULED','LIVE') and ends_at<=now()");
  }
}
