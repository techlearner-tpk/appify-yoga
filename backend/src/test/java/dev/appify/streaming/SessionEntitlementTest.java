package dev.appify.streaming;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import dev.appify.entitlement.SessionEntitlementService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
class SessionEntitlementTest {
  private final JdbcTemplate db=mock(JdbcTemplate.class);
  private final StreamConfigurationService streams=mock(StreamConfigurationService.class);
  private final UUID user=UUID.randomUUID(),slot=UUID.randomUUID();
  private final Instant now=Instant.parse("2026-10-10T03:00:00Z");
  private final SessionEntitlementService service=new SessionEntitlementService(db,streams);
  private Map<String,Object> row() {
    var row=new HashMap<String,Object>();
    row.put("program_id",UUID.randomUUID());row.put("daily_asset_id",UUID.randomUUID());row.put("local_date",Date.valueOf(LocalDate.parse("2026-10-10")));row.put("timezone","Asia/Kolkata");row.put("starts_at",Timestamp.from(now));row.put("ends_at",Timestamp.from(now.plusSeconds(900)));row.put("access_expires_at",Timestamp.from(now.plusSeconds(3600)));row.put("join_early_minutes",5);row.put("close_late_minutes",5);row.put("sequence_number",1);row.put("slot_status","LIVE");row.put("membership_status","TRIAL");row.put("valid_until",Timestamp.from(now.plusSeconds(6000)));row.put("enrolled",true);row.put("provider_type","YOUTUBE");row.put("asset_status","CREATING");return row;
  }
  private void returning(Map<String,Object> row) {when(db.queryForList(anyString(),eq(slot),eq(user))).thenReturn(List.of(row));}
  @Test void zoomDoesNotRequireAYouTubeRecordingAndDisabledConfigurationDeniesAccess() {
    returning(row());when(streams.effective(slot)).thenReturn(new StreamConfiguration("ZOOM",true,"https://zoom.us/j/123",null,null,null,null));
    assertThat(service.evaluate(user,slot,now).allowed()).isTrue();assertThat(service.evaluate(user,slot,now).providerType()).isEqualTo("ZOOM");
    when(streams.effective(slot)).thenReturn(new StreamConfiguration("ZOOM",false,null,null,null,null,null));
    assertThat(service.evaluate(user,slot,now).outcome()).isEqualTo(SessionEntitlementService.Outcome.ASSET_NOT_READY);
  }
  @Test void accountMembershipEnrollmentAndJoinWindowStillApplyBeforeProviderResolution() {
    var r=row();r.put("disabled",true);returning(r);assertThat(service.evaluate(user,slot,now).outcome()).isEqualTo(SessionEntitlementService.Outcome.ACCOUNT_DISABLED);
    r=row();r.put("valid_until",Timestamp.from(now.minusSeconds(1)));returning(r);assertThat(service.evaluate(user,slot,now).outcome()).isEqualTo(SessionEntitlementService.Outcome.MEMBERSHIP_REQUIRED);
    r=row();r.put("enrolled",false);returning(r);assertThat(service.evaluate(user,slot,now).outcome()).isEqualTo(SessionEntitlementService.Outcome.PROGRAM_NOT_ENROLLED);
    r=row();returning(r);assertThat(service.evaluate(user,slot,now.minusSeconds(301)).outcome()).isEqualTo(SessionEntitlementService.Outcome.SESSION_NOT_OPEN);
    r.put("joined_session_slot_id",UUID.randomUUID());returning(r);assertThat(service.evaluate(user,slot,now).outcome()).isEqualTo(SessionEntitlementService.Outcome.ALREADY_ATTENDED_TODAY);
    verifyNoInteractions(streams);
  }
}
