package dev.appify.streaming;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Component;
@Component
public class ZoomSessionJoinProvider implements SessionJoinProvider {
  private final ZoomSdkAuthorization sdk;
  public ZoomSessionJoinProvider(ZoomSdkAuthorization sdk) {this.sdk=sdk;}
  public int authorizationLifetimeSeconds() {return 120;}
  public String type() {return "ZOOM";}
  public Experience prepareJoin(StreamConfiguration config,UUID user,Duration offset,boolean source) {
    sdk.requireConfigured();
    try {meetingNumber(config);} catch(IllegalArgumentException e) {throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Meeting details need an administrator update. Check the meeting ID and actual passcode.");}
    return new Experience("ZOOM_SDK",null);
  }
  public static String meetingNumber(StreamConfiguration config) {
    URI uri=URI.create(validatedUrl(config.providerJoinUrl()));
    String number=uri.getPath().replaceAll("/$","");number=number.substring(number.lastIndexOf('/')+1);
    if(!number.matches("[0-9]{9,11}")) throw new IllegalArgumentException("Enter a valid Zoom meeting number");
    if(config.providerMeetingId()!=null && !config.providerMeetingId().isBlank() && !number.equals(config.providerMeetingId().replaceAll("[ -]",""))) throw new IllegalArgumentException("Meeting ID must match the meeting URL");
    if(uri.getRawQuery()!=null && java.util.Arrays.stream(uri.getRawQuery().split("&")).anyMatch(p->p.startsWith("pwd=")) && (config.meetingPasscode()==null || config.meetingPasscode().isBlank())) throw new IllegalArgumentException("Enter the actual Zoom meeting passcode separately for in-app playback");
    return number;
  }
  public static String validatedUrl(String supplied) {
    try {
      URI uri=URI.create(supplied.trim());String host=uri.getHost();
      if(!"https".equalsIgnoreCase(uri.getScheme()) || host==null || !(host.equals("zoom.us") || host.endsWith(".zoom.us")) || uri.getUserInfo()!=null || (uri.getPort()!=-1 && uri.getPort()!=443) || uri.getFragment()!=null || !uri.getPath().matches("/(j|wc/j)/[0-9]+/?")) throw new IllegalArgumentException();
      return uri.toASCIIString();
    } catch(Exception e) {throw new IllegalArgumentException("Enter a valid HTTPS Zoom meeting URL");}
  }
}
