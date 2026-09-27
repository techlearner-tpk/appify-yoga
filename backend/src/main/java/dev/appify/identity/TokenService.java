package dev.appify.identity;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TokenService {
  private final SecretKey key;
  public TokenService(@Value("${app.jwt-secret}") String secret) {
    if(secret.getBytes(StandardCharsets.UTF_8).length<32) throw new IllegalArgumentException("JWT_SECRET must be at least 32 bytes");
    key=Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }
  public String issue(UUID id,String role) { return Jwts.builder().subject(id.toString()).claim("role",role).issuedAt(Date.from(Instant.now())).expiration(Date.from(Instant.now().plusSeconds(900))).signWith(key).compact(); }
  public Claims parse(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
}
