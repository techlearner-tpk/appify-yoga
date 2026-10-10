package dev.appify.admin;
import dev.appify.streaming.StreamConfiguration;
import dev.appify.streaming.StreamConfigurationService;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/admin")
public class StreamAdminController {
  private final StreamConfigurationService streams;
  public StreamAdminController(StreamConfigurationService streams) {this.streams=streams;}
  @GetMapping("/programs/{id}/stream") public ResponseEntity<Map<String,Object>> program(@PathVariable UUID id) {return response(true,id);}
  @GetMapping("/sessions/{id}/stream") public ResponseEntity<Map<String,Object>> session(@PathVariable UUID id) {return response(false,id);}
  @PutMapping("/programs/{id}/stream") public ResponseEntity<Map<String,Object>> saveProgram(@PathVariable UUID id,@RequestBody StreamConfiguration config,Authentication auth) {streams.save(true,id,config,(UUID)auth.getPrincipal(),false);return response(true,id);}
  @PutMapping("/sessions/{id}/stream") public ResponseEntity<Map<String,Object>> saveSession(@PathVariable UUID id,@RequestBody StreamConfiguration config,Authentication auth) {streams.save(false,id,config,(UUID)auth.getPrincipal(),false);return response(false,id);}
  private ResponseEntity<Map<String,Object>> response(boolean program,UUID id) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(streams.adminGet(program,id));}
}
