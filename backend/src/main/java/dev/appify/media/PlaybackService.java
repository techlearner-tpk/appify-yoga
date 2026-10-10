package dev.appify.media;

import dev.appify.entitlement.OpaqueTokens;
import dev.appify.entitlement.SessionAccessDenied;
import dev.appify.entitlement.SessionEntitlementService;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class PlaybackService {
  private final JdbcTemplate db;private final SessionEntitlementService entitlements;private final dev.appify.streaming.SessionJoinProviderRegistry providers;private final dev.appify.streaming.StreamConfigurationService streams;
  public PlaybackService(JdbcTemplate db,SessionEntitlementService entitlements,dev.appify.streaming.SessionJoinProviderRegistry providers,dev.appify.streaming.StreamConfigurationService streams) {this.db=db;this.entitlements=entitlements;this.providers=providers;this.streams=streams;}
  public record Playback(String playerType,String embedUrl,String redirectUrl,Instant expiresAt,int heartbeatIntervalSeconds) {}

  public Playback resolve(UUID user,String token) {
    var access=touch(user,token);
    var decision=entitlements.evaluate(user,(UUID)access.get("session_slot_id"),Instant.now());
    var player=experience(user,access,decision);
    boolean embedded="EMBEDDED".equals(player.playerType());
    return new Playback(player.playerType(),embedded?player.destination():null,embedded?null:"/api/provider-access/"+token,(Instant)access.get("effective_expiry"),embedded?60:0);
  }
  public String redirect(UUID user,String token) {
    var access=touch(user,token);
    var decision=entitlements.evaluate(user,(UUID)access.get("session_slot_id"),Instant.now());
    var player=experience(user,access,decision);
    if(!"REDIRECT".equals(player.playerType())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"This session opens inside the application");
    return player.destination();
  }
  private dev.appify.streaming.SessionJoinProvider.Experience experience(UUID user,Map<String,Object> access,SessionEntitlementService.Decision decision) {
    return providers.get(decision.providerType()).prepareJoin(streams.forJoin((UUID)access.get("session_slot_id")),user,Duration.ofSeconds(decision.scheduledOffset(Instant.now())),decision.source());
  }

  public Map<String,Object> heartbeat(UUID user,String token) {
    var access=touch(user,token);
    return Map.of("active",true,"expiresAt",access.get("effective_expiry"));
  }
  public void authorizeSlot(UUID user,String token,UUID slot) {
    var access=touch(user,token);
    if(!providers.get((String)access.get("provider_type")).tracksDuration()) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,"Duration attendance is unavailable for this session");
    if(!slot.equals(access.get("session_slot_id"))) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
  }
  public void complete(UUID user,String token) {
    db.update("update playback_session set status='COMPLETED' where user_id=? and token_hash=? and status='ACTIVE'",user,OpaqueTokens.hash(token));
  }
  private Map<String,Object> touch(UUID user,String token) {
    Instant now=Instant.now();
    var rows=db.queryForList("select id,session_slot_id,expires_at,status,provider_type from playback_session where token_hash=? and user_id=?",OpaqueTokens.hash(token),user);
    if(rows.isEmpty()) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
    var row=rows.get(0);
    if(!"ACTIVE".equals(row.get("status")) || !((Timestamp)row.get("expires_at")).toInstant().isAfter(now)) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_EXPIRED);
    var decision=entitlements.evaluate(user,(UUID)row.get("session_slot_id"),now);
    if(!decision.allowed()) throw new SessionAccessDenied(decision.outcome());
    if(!decision.providerType().equals(row.get("provider_type"))) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_EXPIRED);
    // Redirect grants have a fixed two-minute lifetime; heartbeat must not extend them.
    Instant expires=!providers.get(decision.providerType()).tracksDuration()?((Timestamp)row.get("expires_at")).toInstant():now.plusSeconds(providers.get(decision.providerType()).authorizationLifetimeSeconds());
    if(expires.isAfter(decision.closesAt())) expires=decision.closesAt();
    if(expires.isAfter(decision.accessExpiresAt())) expires=decision.accessExpiresAt();
    if(db.update("update playback_session set last_heartbeat_at=?,expires_at=? where id=? and status='ACTIVE' and expires_at>?",Timestamp.from(now),Timestamp.from(expires),row.get("id"),Timestamp.from(now))==0) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_EXPIRED);
    row.put("effective_expiry",expires);
    return row;
  }
}
