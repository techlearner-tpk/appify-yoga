package dev.appify.entitlement;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JoinLinkService {
  private final JdbcTemplate db;private final SessionEntitlementService entitlements;
  public JoinLinkService(JdbcTemplate db,SessionEntitlementService entitlements) {this.db=db;this.entitlements=entitlements;}
  @Transactional public String create(UUID user,UUID slot) {
    var decision=entitlements.evaluate(user,slot,Instant.now());
    if(decision.outcome()==SessionEntitlementService.Outcome.LOGIN_REQUIRED || decision.outcome()==SessionEntitlementService.Outcome.SESSION_NOT_FOUND || decision.outcome()==SessionEntitlementService.Outcome.PROGRAM_NOT_ENROLLED || decision.outcome()==SessionEntitlementService.Outcome.MEMBERSHIP_REQUIRED || decision.outcome()==SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY || decision.outcome()==SessionEntitlementService.Outcome.ACCOUNT_DISABLED || decision.outcome()==SessionEntitlementService.Outcome.SESSION_EXPIRED) throw new SessionAccessDenied(decision.outcome());
    String token=OpaqueTokens.create();Instant expires=decision.closesAt().isBefore(decision.accessExpiresAt())?decision.closesAt():decision.accessExpiresAt();
    db.update("insert into session_join_link(id,token_hash,user_id,session_slot_id,program_id,local_date,expires_at) values(?,?,?,?,?,?,?)",UUID.randomUUID(),OpaqueTokens.hash(token),user,slot,decision.programId(),java.sql.Date.valueOf(decision.localDate()),Timestamp.from(expires));
    return token;
  }
  public Map<String,Object> resolve(UUID user,String token) {
    var rows=db.queryForList("select session_slot_id,expires_at,revoked from session_join_link where token_hash=? and user_id=?",OpaqueTokens.hash(token),user);
    if(rows.isEmpty()) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
    var row=rows.get(0);
    if(Boolean.TRUE.equals(row.get("revoked")) || !((Timestamp)row.get("expires_at")).toInstant().isAfter(Instant.now())) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_EXPIRED);
    UUID slot=(UUID)row.get("session_slot_id");var decision=entitlements.evaluate(user,slot,Instant.now());
    if(decision.outcome()!=SessionEntitlementService.Outcome.ALLOW && decision.outcome()!=SessionEntitlementService.Outcome.SESSION_NOT_OPEN && decision.outcome()!=SessionEntitlementService.Outcome.ASSET_NOT_READY) throw new SessionAccessDenied(decision.outcome());
    return Map.of("slotId",slot);
  }
}
