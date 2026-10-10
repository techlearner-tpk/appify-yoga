package dev.appify.streaming;

/** Internal/admin-only. Never include this type in a member session response. */
public record StreamConfiguration(String providerType,boolean enabled,String providerJoinUrl,String providerMeetingId,String providerVideoId,String meetingPasscode,String technicalNotes) {
  @Override public String toString() {return "StreamConfiguration[redacted]";}
}
