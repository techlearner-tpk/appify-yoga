package dev.appify.media;

import java.time.Duration;

/** Provider operations deliberately have no membership or attendance decisions. */
public interface VideoProvider {
  String type();
  String normalizeAssetId(String supplied);
  default void prepareDailyAsset(String assetId) {}
  default void startSourceSession(String assetId) {}
  PlaybackConfiguration getAuthorizedPlayback(String assetId,Duration offset,boolean source);
  Readiness checkAssetReadiness(String assetId);
  default Readiness getAssetStatus(String assetId) {return checkAssetReadiness(assetId);}
  default void endSourceSession(String assetId) {}
  void deleteAsset(String assetId);
  record PlaybackConfiguration(String playerType,String embedUrl) {}
  enum Readiness { AVAILABLE, PROCESSING, FAILED, UNKNOWN }
}
