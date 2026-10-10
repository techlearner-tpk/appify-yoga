package dev.appify.entitlement;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** The only member playback authorization policy. Provider adapters never make entitlement decisions. */
@Service
public class SessionEntitlementService {
  private final JdbcTemplate db;private final dev.appify.streaming.StreamConfigurationService streams;
  public SessionEntitlementService(JdbcTemplate db,dev.appify.streaming.StreamConfigurationService streams) {this.db=db;this.streams=streams;}

  public enum Outcome { ALLOW,LOGIN_REQUIRED,MEMBERSHIP_REQUIRED,PROGRAM_NOT_ENROLLED,SESSION_NOT_FOUND,SESSION_NOT_OPEN,SESSION_EXPIRED,ALREADY_ATTENDED_TODAY,ASSET_NOT_READY,ACCOUNT_DISABLED }
  public record Decision(Outcome outcome,UUID slotId,UUID programId,UUID assetId,LocalDate localDate,String timezone,Instant startsAt,Instant endsAt,Instant closesAt,Instant accessExpiresAt,boolean source,String providerType,String providerAssetId) {
    public boolean allowed() {return outcome==Outcome.ALLOW;}
    public Decision deny(Outcome reason) {return new Decision(reason,slotId,programId,assetId,localDate,timezone,startsAt,endsAt,closesAt,accessExpiresAt,source,providerType,providerAssetId);}
    public long scheduledOffset(Instant now) {return source?0:Math.max(0,Math.min(Duration.between(startsAt,endsAt).getSeconds(),Duration.between(startsAt,now).getSeconds()));}
  }

  public Decision evaluate(UUID userId,UUID slotId,Instant now) {
    if(userId==null) return new Decision(Outcome.LOGIN_REQUIRED,slotId,null,null,null,null,null,null,null,null,false,null,null);
    var rows=db.queryForList("""
      select s.id,s.program_id,s.daily_asset_id,s.local_date,s.timezone,s.starts_at,s.ends_at,s.sequence_number,s.status as slot_status,
             a.source_session_slot_id,a.asset_status,a.recording_status,a.access_expires_at,a.provider_type,a.provider_asset_id,
             coalesce(p.join_early_minutes,5) as join_early_minutes,coalesce(p.close_late_minutes,5) as close_late_minutes,
             u.disabled,m.status as membership_status,m.valid_until,
             exists(select 1 from program_enrollment e where e.user_id=u.id and e.program_id=s.program_id and e.active=true) as enrolled,
             d.joined_session_slot_id
      from session s join daily_session_asset a on a.id=s.daily_asset_id
      left join program_session_policy p on p.program_id=s.program_id
      cross join app_user u left join membership_entitlement m on m.user_id=u.id
      left join user_daily_participation d on d.user_id=u.id and d.program_id=s.program_id and d.local_date=s.local_date
      where s.id=? and u.id=?
      """,slotId,userId);
    if(rows.isEmpty()) return new Decision(Outcome.SESSION_NOT_FOUND,slotId,null,null,null,null,null,null,null,null,false,null,null);
    Map<String,Object> r=rows.get(0);
    Instant start=((Timestamp)r.get("starts_at")).toInstant(),end=((Timestamp)r.get("ends_at")).toInstant();
    Instant close=end.plusSeconds(((Number)r.get("close_late_minutes")).longValue()*60);
    boolean source=slotId.equals(r.get("source_session_slot_id")) || (r.get("source_session_slot_id")==null && ((Number)r.get("sequence_number")).intValue()==1);
    Instant assetExpires=((Timestamp)r.get("access_expires_at")).toInstant();
    Decision allowed=new Decision(Outcome.ALLOW,slotId,(UUID)r.get("program_id"),(UUID)r.get("daily_asset_id"),((Date)r.get("local_date")).toLocalDate(),(String)r.get("timezone"),start,end,close,assetExpires,source,(String)r.get("provider_type"),(String)r.get("provider_asset_id"));
    if(Boolean.TRUE.equals(r.get("disabled"))) return allowed.deny(Outcome.ACCOUNT_DISABLED);
    if("CANCELLED".equals(r.get("slot_status"))) return allowed.deny(Outcome.SESSION_EXPIRED);
    if(!"ACTIVE".equals(r.get("membership_status")) && !"TRIAL".equals(r.get("membership_status")) || r.get("valid_until")==null || !((Timestamp)r.get("valid_until")).toInstant().isAfter(now)) return allowed.deny(Outcome.MEMBERSHIP_REQUIRED);
    if(!Boolean.TRUE.equals(r.get("enrolled"))) return allowed.deny(Outcome.PROGRAM_NOT_ENROLLED);
    if(!now.isBefore(assetExpires) || !now.isBefore(close)) return allowed.deny(Outcome.SESSION_EXPIRED);
    if(now.isBefore(start.minusSeconds(((Number)r.get("join_early_minutes")).longValue()*60))) return allowed.deny(Outcome.SESSION_NOT_OPEN);
    UUID joined=(UUID)r.get("joined_session_slot_id");
    if(joined!=null && !joined.equals(slotId)) return allowed.deny(Outcome.ALREADY_ATTENDED_TODAY);
    var configured=streams.effective(slotId);
    if(configured!=null) {
      if(!configured.enabled()) return allowed.deny(Outcome.ASSET_NOT_READY);
      return new Decision(Outcome.ALLOW,slotId,allowed.programId(),allowed.assetId(),allowed.localDate(),allowed.timezone(),start,end,close,assetExpires,source,configured.providerType(),null);
    }
    String status=(String)r.get("asset_status"),assetId=(String)r.get("provider_asset_id");
    if(assetId==null || assetId.isBlank() || (!"AVAILABLE".equals(status) && !(source && ("READY_FOR_SOURCE".equals(status) || "SOURCE_ACTIVE".equals(status))))) return allowed.deny(Outcome.ASSET_NOT_READY);
    return allowed;
  }
}
