package dev.appify.media;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/playback")
public class PlaybackController {
  private final PlaybackService playback;
  public PlaybackController(PlaybackService playback) {this.playback=playback;}
  @GetMapping("/{token}") public ResponseEntity<PlaybackService.Playback> resolve(@PathVariable String token,Authentication auth) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playback.resolve((UUID)auth.getPrincipal(),token));
  }
  @GetMapping("/{token}/open") public ResponseEntity<Void> open(@PathVariable String token,Authentication auth) {
    return ResponseEntity.status(303).cacheControl(CacheControl.noStore()).header("Referrer-Policy","no-referrer").location(java.net.URI.create(playback.redirect((UUID)auth.getPrincipal(),token))).build();
  }
  @PostMapping("/{token}/heartbeat") public ResponseEntity<Map<String,Object>> heartbeat(@PathVariable String token,Authentication auth) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(playback.heartbeat((UUID)auth.getPrincipal(),token));
  }
  @PostMapping("/{token}/complete") public Map<String,Boolean> complete(@PathVariable String token,Authentication auth) {
    playback.complete((UUID)auth.getPrincipal(),token);return Map.of("completed",true);
  }
}
