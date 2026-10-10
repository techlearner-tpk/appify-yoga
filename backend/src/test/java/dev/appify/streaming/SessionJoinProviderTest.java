package dev.appify.streaming;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import dev.appify.media.VideoProvider;
import dev.appify.media.VideoProviderRegistry;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
class SessionJoinProviderTest {
  @Test void resolvesProvidersAndRejectsUnknownTypes() {
    var zoom=new ZoomSessionJoinProvider();var youtube=new YouTubeSessionJoinProvider(mock(VideoProviderRegistry.class));
    var registry=new SessionJoinProviderRegistry(List.of(zoom,youtube));
    assertThat(registry.get("ZOOM")).isSameAs(zoom);assertThat(registry.get("YOUTUBE")).isSameAs(youtube);
    assertThatThrownBy(()->registry.get("OTHER")).isInstanceOf(IllegalArgumentException.class);
    assertThat(zoom.tracksDuration()).isFalse();assertThat(zoom.authorizationLifetimeSeconds()).isEqualTo(120);assertThat(youtube.tracksDuration()).isTrue();
  }
  @Test void zoomAllowsOnlyMeetingDestinationsAndRedactsDiagnostics() {
    String url="https://us02web.zoom.us/j/123456789?pwd=private";
    var config=new StreamConfiguration("ZOOM",true,url,null,null,"secret","host notes");
    var player=new ZoomSessionJoinProvider().prepareJoin(config,UUID.randomUUID(),Duration.ZERO,true);
    assertThat(player.destination()).isEqualTo(url);assertThat(player.playerType()).isEqualTo("REDIRECT");
    assertThat(config.toString()+player.toString()).doesNotContain("private","secret","host notes",url);
    for(String bad:List.of("https://zoom.us.evil.test/j/123","http://zoom.us/j/123","https://zoom.us@evil.test/j/123","https://zoom.us/oauth/authorize","javascript:alert(1)")) assertThatThrownBy(()->ZoomSessionJoinProvider.validatedUrl(bad)).isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining(bad);
  }
  @Test void youtubeUsesExistingEmbeddedPlaybackWithScheduledOffset() {
    var videos=mock(VideoProviderRegistry.class);var provider=mock(VideoProvider.class);when(videos.get("YOUTUBE")).thenReturn(provider);
    when(provider.getAuthorizedPlayback("2afajz1hd-E",Duration.ofSeconds(30),false)).thenReturn(new VideoProvider.PlaybackConfiguration("EMBEDDED","https://www.youtube-nocookie.com/embed/2afajz1hd-E?start=30"));
    var config=new StreamConfiguration("YOUTUBE",true,null,null,"2afajz1hd-E",null,null);
    assertThat(new YouTubeSessionJoinProvider(videos).prepareJoin(config,UUID.randomUUID(),Duration.ofSeconds(30),false).playerType()).isEqualTo("EMBEDDED");
    verify(provider).getAuthorizedPlayback("2afajz1hd-E",Duration.ofSeconds(30),false);
  }
  @Test void encryptionIsRandomAuthenticatedAndDoesNotStorePlaintext() {
    var cipher=new StreamSecrets("","unit-test-key");String raw="https://zoom.us/j/123?pwd=very-private";
    String first=cipher.encrypt(raw),second=cipher.encrypt(raw);
    assertThat(first).doesNotContain(raw,"very-private").isNotEqualTo(second);assertThat(cipher.decrypt(first)).isEqualTo(raw);
    assertThatThrownBy(()->new StreamSecrets("","different-key").decrypt(first)).isInstanceOf(IllegalStateException.class).hasMessageNotContaining(raw);
  }
}
