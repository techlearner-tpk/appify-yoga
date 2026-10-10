package dev.appify.streaming;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class ZoomSessionJoinProvider implements SessionJoinProvider {
  public int authorizationLifetimeSeconds() {return 120;}
  public String type() {return "ZOOM";}
  public Experience prepareJoin(StreamConfiguration config,UUID user,Duration offset,boolean source) {
    return new Experience("REDIRECT",validatedUrl(config.providerJoinUrl()));
  }
  public static String validatedUrl(String supplied) {
    try {
      URI uri=URI.create(supplied.trim());String host=uri.getHost();
      if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || !(host.equals("zoom.us") || host.endsWith(".zoom.us")) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null || !uri.getPath().matches("/(j|wc/j)/[0-9]+/?")) throw new IllegalArgumentException();
      return uri.toASCIIString();
    } catch(Exception e) {throw new IllegalArgumentException("Enter a valid HTTPS Zoom meeting URL");}
  }
}
