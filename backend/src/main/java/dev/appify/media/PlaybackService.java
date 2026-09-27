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
  private final JdbcTemplate db;private final SessionEntitlementService entitlements;private final VideoProviderRegistry providers;
  public PlaybackService(JdbcTemplate db,SessionEntitlementService entitlements,VideoProviderRegistry providers) {this.db=db;this.entitlements=entitlements;this.providers=providers;}
  public record Playback(String playerType,String embedUrl,Instant expiresAt,int heartbeatIntervalSeconds) {}

  public Playback resolve(UUID user,String token) {
    var access=touch(user,token);
    var decision=entitlements.evaluate(user,(UUID)access.get("session_slot_id"),Instant.now());
    var player=providers.get(decision.providerType()).getAuthorizedPlayback(decision.providerAssetId(),Duration.ofSeconds(decision.scheduledOffset(Instant.now())),decision.source());
    return new Playback(player.playerType(),player.embedUrl(),(Instant)access.get("effective_expiry"),60);
  }
  public Map<String,Object> heartbeat(UUID user,String token) {
    var access=touch(user,token);
    return Map.of("active",true,"expiresAt",access.get("effective_expiry"));
  }
  public void authorizeSlot(UUID user,String token,UUID slot) {
    var access=touch(user,token);
    if(!slot.equals(access.get("session_slot_id"))) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
  }
  public void complete(UUID user,String token) {
    db.update("update playback_session set status='COMPLETED' where user_id=? and token_hash=? and status='ACTIVE'",user,OpaqueTokens.hash(token));
  }
  private Map<String,Object> touch(UUID user,String token) {
    Instant now=Instant.now();
    var rows=db.queryForList("select id,session_slot_id,expires_at,status from playback_session where token_hash=? and user_id=?",OpaqueTokens.hash(token),user);
    if(rows.isEmpty()) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
    var row=rows.get(0);
    if(!"ACTIVE".equals(row.get("status")) || !((Timestamp)row.get("expires_at")).toInstant().isAfter(now)) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_EXPIRED);
    var decision=entitlements.evaluate(user,(UUID)row.get("session_slot_id"),now);
    if(!decision.allowed()) throw new SessionAccessDenied(decision.outcome());
    Instant expires=now.plusSeconds(900);
    if(expires.isAfter(decision.closesAt())) expires=decision.closesAt();
    if(expires.isAfter(decision.accessExpiresAt())) expires=decision.accessExpiresAt();
    db.update("update playback_session set last_heartbeat_at=?,expires_at=? where id=? and status='ACTIVE'",Timestamp.from(now),Timestamp.from(expires),row.get("id"));
    row.put("effective_expiry",expires);
    return row;
  }
}
