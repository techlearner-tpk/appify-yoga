package dev.appify.media;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VideoProviderRegistry {
  private final List<VideoProvider> providers;
  public VideoProviderRegistry(List<VideoProvider> providers) {this.providers=providers;}
  public VideoProvider get(String type) {
    return providers.stream().filter(provider->provider.type().equals(type)).findFirst()
      .orElseThrow(()->new IllegalStateException("No video provider configured for "+type));
  }
}
