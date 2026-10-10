package dev.appify.streaming;

import java.time.Duration;
import java.util.UUID;

/** Only prepares an experience after the shared application policy has authorized access. */
public interface SessionJoinProvider {
  String type();
  default int authorizationLifetimeSeconds() {return 900;}
  default boolean tracksDuration() {return false;}
  Experience prepareJoin(StreamConfiguration configuration,UUID user,Duration offset,boolean source);
  record Experience(String playerType,String destination) {
    @Override public String toString() {return "Experience[redacted]";}
  }
}
