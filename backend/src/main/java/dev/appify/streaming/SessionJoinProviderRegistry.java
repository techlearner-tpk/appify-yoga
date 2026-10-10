package dev.appify.streaming;
import java.util.List;
import org.springframework.stereotype.Component;
@Component
public class SessionJoinProviderRegistry {
  private final List<SessionJoinProvider> providers;
  public SessionJoinProviderRegistry(List<SessionJoinProvider> providers) {this.providers=List.copyOf(providers);}
  public SessionJoinProvider get(String type) {
    return providers.stream().filter(p->p.type().equals(type)).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported streaming provider"));
  }
}
