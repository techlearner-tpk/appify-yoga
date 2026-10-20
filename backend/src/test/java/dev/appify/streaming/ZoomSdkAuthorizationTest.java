package dev.appify.streaming;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class ZoomSdkAuthorizationTest {
  private final ObjectMapper json=new ObjectMapper();
  private StreamConfiguration config(String meeting,String passcode) {return new StreamConfiguration("ZOOM",true,"https://zoom.us/j/12345678901?pwd=encoded",meeting,null,passcode,null);}
  @Test void signsOnlyParticipantForConfiguredMeetingWithMinimumZoomLifetime() throws Exception {
    String secret="unit-only-sdk-secret-do-not-use-in-production";
    Instant now=Instant.parse("2026-10-20T12:00:00Z");var grant=new ZoomSdkAuthorization("unit-client",secret,json).prepare(config("123 4567 8901","actual-passcode"),"Member Name",now);
    String[] parts=grant.signature().split("\\.");
    var claims=json.readTree(Base64.getUrlDecoder().decode(parts[1]));
    assertThat(claims.get("role").asInt()).isZero();assertThat(claims.get("mn").asText()).isEqualTo("12345678901");assertThat(claims.get("appKey").asText()).isEqualTo("unit-client");
    assertThat(claims.get("exp").asLong()-claims.get("iat").asLong()).isEqualTo(1800);
    assertThat(claims.get("tokenExp")).isEqualTo(claims.get("exp"));
    Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
    assertThat(Base64.getUrlDecoder().decode(parts[2])).isEqualTo(mac.doFinal((parts[0]+"."+parts[1]).getBytes(StandardCharsets.UTF_8)));
    assertThat(grant.password()).isEqualTo("actual-passcode");assertThat(grant.userName()).isEqualTo("Member Name");
    assertThat(grant.toString()).doesNotContain(grant.signature(),"actual-passcode",secret,"12345678901");
  }
  @Test void refusesUnconfiguredSdkAndMismatchedOrIncompleteMeetingDetails() {
    assertThatThrownBy(()->new ZoomSdkAuthorization("","",json).prepare(config(null,"pass"),"Member",Instant.now())).isInstanceOf(ResponseStatusException.class);
    assertThatThrownBy(()->ZoomSessionJoinProvider.meetingNumber(config("99999999999","pass"))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(()->ZoomSessionJoinProvider.meetingNumber(config(null,""))).isInstanceOf(IllegalArgumentException.class);
  }
}
