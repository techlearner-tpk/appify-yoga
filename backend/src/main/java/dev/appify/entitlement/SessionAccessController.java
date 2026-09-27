package dev.appify.entitlement;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/session-slots")
public class SessionAccessController {
  private final SessionJoinService joins;
  public SessionAccessController(SessionJoinService joins) {this.joins=joins;}
  @PostMapping("/{slotId}/select") public ResponseEntity<Map<String,Object>> select(@PathVariable UUID slotId,Authentication auth) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(joins.select((UUID)auth.getPrincipal(),slotId));
  }
  @PostMapping("/{slotId}/join") public ResponseEntity<SessionJoinService.JoinResult> join(@PathVariable UUID slotId,Authentication auth) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(joins.join((UUID)auth.getPrincipal(),slotId));
  }
}
