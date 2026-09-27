package dev.appify.entitlement;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionDecisionTest {
  private final Instant start=Instant.parse("2026-09-27T01:00:00Z"),end=Instant.parse("2026-09-27T01:45:00Z");
  @Test void laterSlotStartsAtScheduledOffsetAndCapsAtDuration() {
    var decision=new SessionEntitlementService.Decision(SessionEntitlementService.Outcome.ALLOW,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),LocalDate.parse("2026-09-27"),"Asia/Kolkata",start,end,end,end,false,"YOUTUBE","hidden");
    assertThat(decision.scheduledOffset(start.minusSeconds(30))).isZero();
    assertThat(decision.scheduledOffset(start.plusSeconds(600))).isEqualTo(600);
    assertThat(decision.scheduledOffset(end.plusSeconds(300))).isEqualTo(2700);
  }
  @Test void sourceSlotAlwaysBeginsAtZero() {
    var decision=new SessionEntitlementService.Decision(SessionEntitlementService.Outcome.ALLOW,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),LocalDate.parse("2026-09-27"),"Asia/Kolkata",start,end,end,end,true,"YOUTUBE","hidden");
    assertThat(decision.scheduledOffset(start.plusSeconds(600))).isZero();
  }
}
