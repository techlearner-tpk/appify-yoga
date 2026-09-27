package dev.appify.entitlement;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/join-links")
public class JoinLinkController {
  private final JoinLinkService links;
  public JoinLinkController(JoinLinkService links) {this.links=links;}
  @PostMapping("/{slotId}") public ResponseEntity<Map<String,String>> create(@PathVariable UUID slotId,Authentication auth) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("token",links.create((UUID)auth.getPrincipal(),slotId)));}
  @GetMapping("/{token}") public ResponseEntity<Map<String,Object>> resolve(@PathVariable String token,Authentication auth) {return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(links.resolve((UUID)auth.getPrincipal(),token));}
}
