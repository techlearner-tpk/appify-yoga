package dev.appify.entitlement;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class OpaqueTokens {
  private static final SecureRandom RANDOM=new SecureRandom();
  private OpaqueTokens() {}
  public static String create() {byte[] value=new byte[32];RANDOM.nextBytes(value);return Base64.getUrlEncoder().withoutPadding().encodeToString(value);}
  public static String hash(String value) {
    if(value==null || !value.matches("[A-Za-z0-9_-]{43}")) throw new SessionAccessDenied(SessionEntitlementService.Outcome.SESSION_NOT_FOUND);
    try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII)));}
    catch(Exception e) {throw new IllegalStateException(e);}
  }
}
