package dev.appify.entitlement;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import dev.appify.media.PlaybackService;
import dev.appify.streaming.SessionJoinProviderRegistry;
import dev.appify.streaming.StreamConfigurationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class JoinAuthorizationTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final SessionEntitlementService policy=mock(SessionEntitlementService.class);
  private final String token=OpaqueTokens.create();
  private final UUID user=UUID.randomUUID(),slot=UUID.randomUUID();
  @Test void expiredAndRevokedReminderTokensFailBeforeEntitlementLookup() {
    for(boolean revoked:List.of(false,true)) {
      when(db.queryForList(anyString(),anyString(),any(UUID.class))).thenReturn(List.of(Map.of("session_slot_id",slot,"expires_at",Timestamp.from(revoked?Instant.now().plusSeconds(100):Instant.now().minusSeconds(1)),"revoked",revoked)));
      assertThatThrownBy(()->new JoinLinkService(db,policy).resolve(user,token)).isInstanceOf(SessionAccessDenied.class);
    }
    verifyNoInteractions(policy);
  }
  @Test void tokenLookupBindsUserAndHashesRawValue() {
    when(db.queryForList(anyString(),anyString(),any(UUID.class))).thenReturn(List.of());
    assertThatThrownBy(()->new JoinLinkService(db,policy).resolve(user,token)).isInstanceOf(SessionAccessDenied.class);
    verify(db).queryForList(anyString(),eq(OpaqueTokens.hash(token)),eq(user));verifyNoInteractions(policy);
  }
  @Test void expiredRedirectAuthorizationCannotOpenProvider() {
    var row=new HashMap<String,Object>();row.put("session_slot_id",slot);row.put("expires_at",Timestamp.from(Instant.now().minusSeconds(1)));row.put("status","ACTIVE");
    when(db.queryForList(anyString(),anyString(),any(UUID.class))).thenReturn(List.of(row));
    var providers=mock(SessionJoinProviderRegistry.class);var streams=mock(StreamConfigurationService.class);
    assertThatThrownBy(()->new PlaybackService(db,policy,providers,streams).redirect(user,token)).isInstanceOf(SessionAccessDenied.class);
    verifyNoInteractions(providers,streams,policy);
  }
  @Test void redirectHeartbeatCannotExtendItsFixedAuthorizationExpiry() {
    Instant expiry=Instant.now().plusSeconds(30);
    var row=new HashMap<String,Object>();row.put("id",UUID.randomUUID());row.put("session_slot_id",slot);row.put("expires_at",Timestamp.from(expiry));row.put("status","ACTIVE");row.put("provider_type","ZOOM");
    when(db.queryForList(anyString(),anyString(),any(UUID.class))).thenReturn(List.of(row));
    when(db.update(anyString(),any(Timestamp.class),any(Timestamp.class),any(UUID.class),any(Timestamp.class))).thenReturn(1);
    when(policy.evaluate(eq(user),eq(slot),any())).thenReturn(new SessionEntitlementService.Decision(SessionEntitlementService.Outcome.ALLOW,slot,null,null,null,null,null,null,Instant.now().plusSeconds(900),Instant.now().plusSeconds(3600),false,"ZOOM",null));
    var providers=new SessionJoinProviderRegistry(List.of(new dev.appify.streaming.ZoomSessionJoinProvider()));
    var result=new PlaybackService(db,policy,providers,mock(StreamConfigurationService.class)).heartbeat(user,token);
    assertThat(result.get("expiresAt")).isEqualTo(expiry);
  }
  @Test void rejectedEntitlementDoesNotRecordAttendanceOrPrepareProvider() {
    var providers=mock(SessionJoinProviderRegistry.class);var streams=mock(StreamConfigurationService.class);
    for(var reason:List.of(SessionEntitlementService.Outcome.MEMBERSHIP_REQUIRED,SessionEntitlementService.Outcome.PROGRAM_NOT_ENROLLED,SessionEntitlementService.Outcome.ACCOUNT_DISABLED,SessionEntitlementService.Outcome.SESSION_NOT_OPEN)) {
      when(policy.evaluate(eq(user),eq(slot),any())).thenReturn(new SessionEntitlementService.Decision(reason,slot,null,null,null,null,null,null,null,null,false,null,null));
      assertThatThrownBy(()->new SessionJoinService(db,policy,new SimpleMeterRegistry(),providers,streams).join(user,slot)).isInstanceOf(SessionAccessDenied.class);
    }
    verifyNoInteractions(providers,streams);verify(db,never()).update(anyString(),any(Object[].class));
  }
}
