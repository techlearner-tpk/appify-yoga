package dev.appify.scheduling;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import dev.appify.entitlement.SessionAccessDenied;
import dev.appify.entitlement.SessionEntitlementService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class MemberSessionServiceTest {
  @Test void existingClassWithoutEnrollmentReportsEnrollmentRequired() {
    var service=serviceWithOutcome(SessionEntitlementService.Outcome.PROGRAM_NOT_ENROLLED);
    var denied=assertThrows(SessionAccessDenied.class,()->service.one(USER,SLOT));
    assertEquals(SessionEntitlementService.Outcome.PROGRAM_NOT_ENROLLED,denied.outcome());
    assertEquals(HttpStatus.FORBIDDEN,denied.status());
  }

  @Test void unknownClassStillReportsNotFound() {
    var service=serviceWithOutcome(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
    var denied=assertThrows(ResponseStatusException.class,()->service.one(USER,SLOT));
    assertEquals(HttpStatus.NOT_FOUND,denied.getStatusCode());
  }

  private static final UUID USER=UUID.randomUUID(),SLOT=UUID.randomUUID();
  private MemberSessionService serviceWithOutcome(SessionEntitlementService.Outcome outcome) {
    var db=mock(JdbcTemplate.class);
    when(db.queryForList(anyString(),eq(USER),eq(SLOT))).thenReturn(List.of());
    var entitlements=mock(SessionEntitlementService.class);
    when(entitlements.evaluate(eq(USER),eq(SLOT),any(Instant.class)))
        .thenReturn(new SessionEntitlementService.Decision(outcome,SLOT,null,null,null,null,null,null,null,null,false,null,null));
    return new MemberSessionService(db,entitlements);
  }
}
