package dev.appify.attendance;

import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttendanceRulesTest {
  @Test void qualificationUsesConfiguredThreshold() {
    assertThat(AttendanceRules.qualifies(42,60,70)).isTrue();
    assertThat(AttendanceRules.qualifies(41,60,70)).isFalse();
    assertThat(AttendanceRules.qualifies(60,60,100)).isTrue();
  }
  @Test void duplicateAndOutOfOrderHeartbeatsCannotCreditTime() {
    Instant last=Instant.parse("2026-01-01T10:00:00Z");
    assertThat(AttendanceRules.credit(last,last,last.plusSeconds(180))).isZero();
    assertThat(AttendanceRules.credit(last,last.minusSeconds(30),last.plusSeconds(180))).isZero();
    assertThat(AttendanceRules.credit(last,last.plusSeconds(300),last.plusSeconds(180))).isEqualTo(120);
  }
  @Test void attendanceStopsAtSessionEnd() {
    Instant last=Instant.parse("2026-01-01T10:00:00Z");
    assertThat(AttendanceRules.credit(last,last.plusSeconds(90),last.plusSeconds(40))).isEqualTo(40);
  }
  @Test void invalidRuleIsRejected() {assertThatThrownBy(()->AttendanceRules.qualifies(1,0,70)).isInstanceOf(IllegalArgumentException.class);}
}
