package dev.appify.entitlement;

import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionJoinService {
  private final JdbcTemplate db;private final SessionEntitlementService entitlements;private final MeterRegistry metrics;private final dev.appify.streaming.SessionJoinProviderRegistry providers;private final dev.appify.streaming.StreamConfigurationService streams;
  public SessionJoinService(JdbcTemplate db,SessionEntitlementService entitlements,MeterRegistry metrics,dev.appify.streaming.SessionJoinProviderRegistry providers,dev.appify.streaming.StreamConfigurationService streams) {this.db=db;this.entitlements=entitlements;this.metrics=metrics;this.providers=providers;this.streams=streams;}
  public record JoinResult(UUID sessionSlotId,UUID accessSessionId,String playerType,String playbackToken,int heartbeatIntervalSeconds,boolean concurrentWarning) {}

  @Transactional public Map<String,Object> select(UUID user,UUID slot) {
    var decision=entitlements.evaluate(user,slot,Instant.now());
    switch(decision.outcome()) {
      case ACCOUNT_DISABLED,LOGIN_REQUIRED,MEMBERSHIP_REQUIRED,PROGRAM_NOT_ENROLLED,SESSION_NOT_FOUND,SESSION_EXPIRED,ALREADY_ATTENDED_TODAY -> throw new SessionAccessDenied(decision.outcome());
      default -> {}
    }
    ensureDailyRow(user,decision);
    UUID joined=db.queryForObject("select joined_session_slot_id from user_daily_participation where user_id=? and program_id=? and local_date=? for update",UUID.class,user,decision.programId(),Date.valueOf(decision.localDate()));
    if(joined!=null && !joined.equals(slot)) throw new SessionAccessDenied(SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY);
    db.update("update user_daily_participation set selected_session_slot_id=?,updated_at=now() where user_id=? and program_id=? and local_date=?",slot,user,decision.programId(),Date.valueOf(decision.localDate()));
    return Map.of("slotId",slot,"selected",true);
  }

  @Transactional public JoinResult join(UUID user,UUID slot) {
    metrics.counter("session_join_attempts").increment();
    db.queryForList("select id from session where id=? for update",slot);
    Instant now=Instant.now();var decision=entitlements.evaluate(user,slot,now);
    if(!decision.allowed()) {denied(decision.outcome());throw new SessionAccessDenied(decision.outcome());}
    ensureDailyRow(user,decision);
    UUID joined=db.queryForObject("select joined_session_slot_id from user_daily_participation where user_id=? and program_id=? and local_date=? for update",UUID.class,user,decision.programId(),Date.valueOf(decision.localDate()));
    if(joined!=null && !joined.equals(slot)) {denied(SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY);throw new SessionAccessDenied(SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY);}
    // Recheck after the unique daily row is locked: two devices may have raced to join different slots.
    decision=entitlements.evaluate(user,slot,now);
    if(!decision.allowed()) {denied(decision.outcome());throw new SessionAccessDenied(decision.outcome());}
    var provider=providers.get(decision.providerType());
    var experience=provider.prepareJoin(streams.forJoin(slot),user,java.time.Duration.ofSeconds(decision.scheduledOffset(now)),decision.source());
    db.update("update user_daily_participation set selected_session_slot_id=?,joined_session_slot_id=?,status=case when joined_session_slot_id is null then 'JOINED' else status end,first_joined_at=coalesce(first_joined_at,?),updated_at=now() where user_id=? and program_id=? and local_date=?",slot,slot,Timestamp.from(now),user,decision.programId(),Date.valueOf(decision.localDate()));
    db.update("insert into attendance(id,user_id,session_id,joined_at,last_seen_at) values(?,?,?,?,?) on conflict(user_id,session_id) do nothing",UUID.randomUUID(),user,slot,Timestamp.from(now),Timestamp.from(now));
    db.update("update attendance set last_seen_at=?,left_at=null,updated_at=now() where user_id=? and session_id=? and left_at is not null",Timestamp.from(now),user,slot);
    db.update("update attendance set provider_type=? where user_id=? and session_id=?",decision.providerType(),user,slot);
    int previous=db.update("update playback_session set status='REVOKED' where user_id=? and status='ACTIVE' and expires_at>?",user,Timestamp.from(now));
    Instant expires=now.plusSeconds(provider.authorizationLifetimeSeconds());
    if(expires.isAfter(decision.closesAt())) expires=decision.closesAt();
    if(expires.isAfter(decision.accessExpiresAt())) expires=decision.accessExpiresAt();
    UUID accessId=UUID.randomUUID();String token=OpaqueTokens.create();
    db.update("insert into playback_session(id,user_id,session_slot_id,daily_session_asset_id,token_hash,expires_at,concurrent_warning,provider_type) values(?,?,?,?,?,?,?,?)",accessId,user,slot,decision.assetId(),OpaqueTokens.hash(token),Timestamp.from(expires),previous>0,decision.providerType());
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),user,previous>0?"SESSION_JOINED_CONCURRENT":"SESSION_JOINED","session",slot);
    metrics.counter("session_join_allowed").increment();
    return new JoinResult(slot,accessId,experience.playerType(),token,provider.tracksDuration()?60:0,previous>0);
  }
  private void ensureDailyRow(UUID user,SessionEntitlementService.Decision decision) {
    db.update("insert into user_daily_participation(id,user_id,program_id,local_date,timezone) values(?,?,?,?,?) on conflict(user_id,program_id,local_date) do nothing",UUID.randomUUID(),user,decision.programId(),Date.valueOf(decision.localDate()),decision.timezone());
  }
  private void denied(SessionEntitlementService.Outcome reason) {
    metrics.counter("session_join_denied","reason",reason.name()).increment();
    if(reason==SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY) metrics.counter("join_denied_already_attended").increment();
  }
}
