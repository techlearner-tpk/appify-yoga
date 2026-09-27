package dev.appify.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class YouTubeVideoProvider implements VideoProvider {
  private static final Pattern ID=Pattern.compile("[A-Za-z0-9_-]{11}");
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json;
  private final String clientId,clientSecret,refreshToken;
  public YouTubeVideoProvider(ObjectMapper json,@Value("${app.youtube.client-id:}") String clientId,@Value("${app.youtube.client-secret:}") String clientSecret,@Value("${app.youtube.refresh-token:}") String refreshToken) {
    this.json=json;this.clientId=clientId;this.clientSecret=clientSecret;this.refreshToken=refreshToken;
  }
  @Override public String type() {return "YOUTUBE";}
  @Override public String normalizeAssetId(String supplied) {
    if(supplied==null || supplied.isBlank()) throw new IllegalArgumentException("Video URL or ID required");
    String raw=supplied.trim();
    if(ID.matcher(raw).matches()) return raw;
    try {
      URI uri=URI.create(raw);String host=uri.getHost();
      if(host==null || !"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException();
      if(host.equals("youtu.be")) raw=uri.getPath().replaceFirst("^/","");
      else if(host.equals("youtube.com") || host.equals("www.youtube.com") || host.equals("m.youtube.com")) {
        String path=uri.getPath();
        if(path.startsWith("/live/") || path.startsWith("/embed/")) raw=path.substring(path.lastIndexOf('/')+1);
        else if(path.equals("/watch") && uri.getRawQuery()!=null) {
          raw="";for(String pair:uri.getRawQuery().split("&")) if(pair.startsWith("v=")) raw=pair.substring(2);
        }
      }
    } catch(Exception ignored) {throw new IllegalArgumentException("Invalid YouTube URL or video ID");}
    if(!ID.matcher(raw).matches()) throw new IllegalArgumentException("Invalid YouTube URL or video ID");
    return raw;
  }
  @Override public PlaybackConfiguration getAuthorizedPlayback(String assetId,Duration offset,boolean source) {
    String id=normalizeAssetId(assetId);
    long seconds=source?0:Math.max(0,offset.getSeconds());
    return new PlaybackConfiguration("EMBEDDED","https://www.youtube-nocookie.com/embed/"+id+"?autoplay=1&controls=0&disablekb=1&rel=0&start="+seconds);
  }
  @Override public Readiness checkAssetReadiness(String assetId) {
    if(!configured()) return Readiness.UNKNOWN;
    try {
      String uri="https://www.googleapis.com/youtube/v3/videos?part=status,processingDetails&id="+normalizeAssetId(assetId);
      var response=http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+accessToken()).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()!=200) return Readiness.UNKNOWN;
      JsonNode items=json.readTree(response.body()).path("items");
      if(!items.isArray() || items.isEmpty()) return Readiness.PROCESSING;
      String processing=items.get(0).path("processingDetails").path("processingStatus").asText("");
      String upload=items.get(0).path("status").path("uploadStatus").asText("");
      if("failed".equals(processing) || "rejected".equals(upload)) return Readiness.FAILED;
      if("succeeded".equals(processing) || "processed".equals(upload)) return Readiness.AVAILABLE;
      return Readiness.PROCESSING;
    } catch(Exception ignored) {return Readiness.UNKNOWN;}
  }
  @Override public void deleteAsset(String assetId) {
    if(!configured()) throw new IllegalStateException("YouTube OAuth credentials are not configured");
    try {
      String uri="https://www.googleapis.com/youtube/v3/videos?id="+normalizeAssetId(assetId);
      var response=http.send(HttpRequest.newBuilder(URI.create(uri)).header("Authorization","Bearer "+accessToken()).timeout(Duration.ofSeconds(10)).DELETE().build(),HttpResponse.BodyHandlers.discarding());
      if(response.statusCode()!=204 && response.statusCode()!=404) throw new IllegalStateException("YouTube deletion returned HTTP "+response.statusCode());
    } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException("YouTube deletion interrupted");}
    catch(Exception e) {throw new IllegalStateException("YouTube deletion failed: "+e.getMessage());}
  }
  private boolean configured() {return !clientId.isBlank() && !clientSecret.isBlank() && !refreshToken.isBlank();}
  private String accessToken() throws Exception {
    String form="client_id="+encode(clientId)+"&client_secret="+encode(clientSecret)+"&refresh_token="+encode(refreshToken)+"&grant_type=refresh_token";
    var request=HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token")).header("Content-Type","application/x-www-form-urlencoded").timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.ofString(form)).build();
    var response=http.send(request,HttpResponse.BodyHandlers.ofString());
    if(response.statusCode()!=200) throw new IllegalStateException("YouTube OAuth refresh returned HTTP "+response.statusCode());
    String token=json.readTree(response.body()).path("access_token").asText("");
    if(token.isBlank()) throw new IllegalStateException("YouTube OAuth did not return an access token");
    return token;
  }
  private static String encode(String value) {return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
