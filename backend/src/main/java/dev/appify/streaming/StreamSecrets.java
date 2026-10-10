package dev.appify.streaming;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StreamSecrets {
  private final SecretKeySpec key;
  private final SecureRandom random=new SecureRandom();
  public StreamSecrets(@Value("${app.stream-encryption-key:}") String supplied,@Value("${app.jwt-secret}") String jwtSecret) {
    try {
      byte[] bytes=supplied.isBlank()?MessageDigest.getInstance("SHA-256").digest(("session-stream:v1:"+jwtSecret).getBytes(StandardCharsets.UTF_8)):Base64.getDecoder().decode(supplied);
      if(bytes.length!=32) throw new IllegalArgumentException();
      key=new SecretKeySpec(bytes,"AES");
    } catch(Exception e) {throw new IllegalStateException("Stream encryption requires a base64 encoded 32-byte key");}
  }
  public String encrypt(String value) {
    try {
      byte[] nonce=new byte[12];random.nextBytes(nonce);
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
      return "v1:"+Base64.getEncoder().encodeToString(nonce)+":"+Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch(Exception e) {throw new IllegalStateException("Unable to encrypt stream configuration");}
  }
  public String decrypt(String value) {
    try {
      String[] parts=value.split(":");if(parts.length!=3 || !parts[0].equals("v1")) throw new IllegalArgumentException();
      Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.getDecoder().decode(parts[1])));
      return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])),StandardCharsets.UTF_8);
    } catch(Exception e) {throw new IllegalStateException("Unable to read stream configuration");}
  }
}
