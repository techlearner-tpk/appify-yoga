package dev.appify.scheduling;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/sessions")
public class SessionController {
  private final MemberSessionService sessions;
  public SessionController(MemberSessionService sessions) {this.sessions=sessions;}
  @GetMapping public List<Map<String,Object>> list(Authentication auth) {return sessions.upcoming((UUID)auth.getPrincipal());}
  @GetMapping("/{id}") public Map<String,Object> get(@PathVariable UUID id,Authentication auth) {return sessions.one((UUID)auth.getPrincipal(),id);}
}
