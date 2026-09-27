package dev.appify.common;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoSeed implements CommandLineRunner {
  private final JdbcTemplate db; private final String password; private final boolean enabled; private final String video;
  public DemoSeed(JdbcTemplate db,@Value("${app.demo-password}") String password,@Value("${app.seed-demo:true}") boolean enabled,@Value("${app.youtube-video-id}") String video) {this.db=db;this.password=password;this.enabled=enabled;this.video=video;}
  @Override public void run(String... args) {
    if(!enabled) return;
    seedUser("member@example.test","Demo Member","USER","DEMOUSER");
    seedUser("instructor@example.test","Demo Instructor","INSTRUCTOR","DEMOINST");
    seedUser("admin@example.test","Demo Admin","ADMIN","DEMOADMIN");
    UUID demoId=UUID.nameUUIDFromBytes(("demo-session:"+LocalDate.now()).getBytes(StandardCharsets.UTF_8)); Instant start=Instant.now().minusSeconds(15);
    db.update("insert into session(id,program_id,instructor_id,starts_at,ends_at,timezone,status,delivery_type,youtube_video_id) values(?,'20000000-0000-0000-0000-000000000001','10000000-0000-0000-0000-000000000001',?,?,'Asia/Kolkata','LIVE','LIVE',?) on conflict(id) do nothing",demoId,Timestamp.from(start),Timestamp.from(start.plusSeconds(180)),video);
  }
  private void seedUser(String email,String name,String role,String referral) {
    UUID id=UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
    db.update("insert into app_user(id,email,password_hash,display_name,role,referral_code) values(?,?,?,?,?,?) on conflict(email) do nothing",id,email,new BCryptPasswordEncoder().encode(password),name,role,referral);
    db.update("insert into streak(user_id) values(?) on conflict do nothing",id);
    if(role.equals("USER")) db.update("insert into program_enrollment(id,user_id,program_id) values(?,?,'20000000-0000-0000-0000-000000000001') on conflict do nothing",UUID.randomUUID(),id);
  }
}
