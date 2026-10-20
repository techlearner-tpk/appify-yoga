package dev.appify.streaming;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.appify.media.VideoProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class StreamConfigurationValidationTest {
  private final StreamConfigurationService service=new StreamConfigurationService(mock(JdbcTemplate.class),mock(StreamSecrets.class),new ObjectMapper(),mock(VideoProviderRegistry.class));
  private StreamConfiguration zoom(String id,String passcode) {return new StreamConfiguration("ZOOM",true,"https://us04web.zoom.us/j/1234567890?pwd=encoded-private.1",id,null,passcode,null);}
  @Test void acceptsTenDigitZoomInvitationAndDerivesOptionalMeetingId() {
    var config=service.validate(zoom("","actual-private"));
    assertThat(ZoomSessionJoinProvider.meetingNumber(config)).isEqualTo("1234567890");
  }
  @Test void reportsTheExactInvalidFieldWithoutEchoingSecrets() {
    assertThatThrownBy(()->service.validate(zoom("99999999999","actual-private"))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Meeting ID must match the meeting URL").hasMessageNotContaining("encoded-private").hasMessageNotContaining("actual-private").hasMessageNotContaining("99999999999");
    assertThatThrownBy(()->service.validate(zoom("",""))).isInstanceOf(ResponseStatusException.class).hasMessageContaining("Enter the actual Zoom meeting passcode separately").hasMessageNotContaining("encoded-private");
  }
}
