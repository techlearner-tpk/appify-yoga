package dev.appify.attendance;

import java.time.Duration;
import java.time.Instant;

public final class AttendanceRules {
  private AttendanceRules() {}
  public static long credit(Instant lastSeen,Instant now,Instant sessionEnd) {
    if(!now.isAfter(lastSeen)) return 0;
    return Math.max(0,Math.min(120,Duration.between(lastSeen,now.isAfter(sessionEnd)?sessionEnd:now).getSeconds()));
  }
  public static boolean qualifies(long watchedSeconds,long scheduledSeconds,int minimumPercent) {
    if(minimumPercent<1 || minimumPercent>100 || scheduledSeconds<=0) throw new IllegalArgumentException("Invalid attendance rule");
    return watchedSeconds*100 >= scheduledSeconds*(long)minimumPercent;
  }
}
