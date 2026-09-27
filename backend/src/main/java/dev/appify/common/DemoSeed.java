package dev.appify.common;

import dev.appify.media.DailySessionAssetService;
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
  private static final UUID YOGA=UUID.fromString("20000000-0000-0000-0000-000000000001");
  private final JdbcTemplate db; private final DailySessionAssetService assets; private final String password; private final boolean enabled; private final String video;
  public DemoSeed(JdbcTemplate db,DailySessionAssetService assets,@Value("${app.demo-password}") String password,@Value("${app.seed-demo:true}") boolean enabled,@Value("${app.youtube-video-id}") String video) {this.db=db;this.assets=assets;this.password=password;this.enabled=enabled;this.video=video;}
  @Override public void run(String... args) {
    if(!enabled) return;
    seedUser("member@example.test","Demo Member","USER","DEMOUSER");
    seedUser("instructor@example.test","Demo Instructor","INSTRUCTOR","DEMOINST");
    seedUser("admin@example.test","Demo Admin","ADMIN","DEMOADMIN");
    db.update("update instructor set user_id=? where id='10000000-0000-0000-0000-000000000001' and user_id is null",UUID.nameUUIDFromBytes("instructor@example.test".getBytes(StandardCharsets.UTF_8)));
    LocalDate day=LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));UUID asset=assets.ensure(YOGA,day,"Asia/Kolkata");
    if(video!=null && !video.isBlank() && db.queryForObject("select provider_asset_id from daily_session_asset where id=?",String.class,asset)==null) assets.attach(asset,"YOUTUBE",video,false,true);
    UUID demoId=UUID.nameUUIDFromBytes(("demo-session:"+day).getBytes(StandardCharsets.UTF_8)); Instant start=Instant.now().minusSeconds(15);
    db.update("insert into session(id,program_id,instructor_id,starts_at,ends_at,timezone,local_date,daily_asset_id,sequence_number,status,delivery_type) values(?,?,?, ?,?,'Asia/Kolkata',?,?,1,'LIVE','LIVE') on conflict(id) do nothing",demoId,YOGA,UUID.fromString("10000000-0000-0000-0000-000000000001"),Timestamp.from(start),Timestamp.from(start.plusSeconds(180)),java.sql.Date.valueOf(day),asset);
  }
  private void seedUser(String email,String name,String role,String referral) {
    UUID id=UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
    db.update("insert into app_user(id,email,password_hash,display_name,role,referral_code) values(?,?,?,?,?,?) on conflict(email) do nothing",id,email,new BCryptPasswordEncoder().encode(password),name,role,referral);
    db.update("insert into streak(user_id) values(?) on conflict do nothing",id);
    db.update("insert into membership_entitlement(user_id,status,valid_until) values(?,'TRIAL',now()+interval '30 days') on conflict do nothing",id);
    if(role.equals("USER")) db.update("insert into program_enrollment(id,user_id,program_id) values(?,?,'20000000-0000-0000-0000-000000000001') on conflict do nothing",UUID.randomUUID(),id);
  }
}
