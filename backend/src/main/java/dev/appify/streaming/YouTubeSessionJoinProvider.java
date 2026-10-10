package dev.appify.streaming;
import dev.appify.media.VideoProviderRegistry;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class YouTubeSessionJoinProvider implements SessionJoinProvider {
  private final VideoProviderRegistry providers;
  public YouTubeSessionJoinProvider(VideoProviderRegistry providers) {this.providers=providers;}
  public boolean tracksDuration() {return true;}
  public String type() {return "YOUTUBE";}
  public Experience prepareJoin(StreamConfiguration config,UUID user,Duration offset,boolean source) {
    var player=providers.get(type()).getAuthorizedPlayback(config.providerVideoId(),offset,source);
    return new Experience(player.playerType(),player.embedUrl());
  }
}
