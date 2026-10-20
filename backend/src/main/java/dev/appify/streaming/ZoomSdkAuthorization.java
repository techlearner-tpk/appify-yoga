package dev.appify.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Participant-only Zoom signatures; never accepts a client-supplied meeting or role. */
@Component
public class ZoomSdkAuthorization {
  private final String clientId,clientSecret;private final ObjectMapper json;
  public ZoomSdkAuthorization(@Value("${app.zoom.client-id:}") String clientId,@Value("${app.zoom.client-secret:}") String clientSecret,ObjectMapper json) {
    this.clientId=clientId;this.clientSecret=clientSecret;this.json=json;
  }
  public void requireConfigured() {
    if(clientId.isBlank() || clientSecret.isBlank()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"In-app meetings are not configured. Ask your administrator to configure the Zoom Meeting SDK.");
  }
  public record Grant(String signature,String meetingNumber,String password,String userName,Instant expiresAt) {
    @Override public String toString() {return "ZoomSdkGrant[redacted]";}
  }
  public Grant prepare(StreamConfiguration configuration,String userName,Instant now) {
    requireConfigured();String meeting=ZoomSessionJoinProvider.meetingNumber(configuration);
    // Zoom requires at least 30 minutes between iat and exp. App access remains shorter.
    long issued=now.getEpochSecond()-30,expires=issued+1800;
    try {
      String header=encode(json.writeValueAsBytes(Map.of("alg","HS256","typ","JWT")));
      String payload=encode(json.writeValueAsBytes(Map.of("appKey",clientId,"mn",meeting,"role",0,"iat",issued,"exp",expires,"tokenExp",expires)));
      String message=header+"."+payload;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(clientSecret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
      return new Grant(message+"."+encode(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))),meeting,configuration.meetingPasscode()==null?"":configuration.meetingPasscode(),userName,Instant.ofEpochSecond(expires));
    } catch(Exception e) {throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Unable to authorize in-app meeting");}
  }
  private static String encode(byte[] bytes) {return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);}
}
