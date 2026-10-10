package dev.appify.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.appify.media.VideoProviderRegistry;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StreamConfigurationService {
  private final JdbcTemplate db;private final StreamSecrets secrets;private final ObjectMapper json;private final VideoProviderRegistry videos;
  public StreamConfigurationService(JdbcTemplate db,StreamSecrets secrets,ObjectMapper json,VideoProviderRegistry videos) {this.db=db;this.secrets=secrets;this.json=json;this.videos=videos;}
  public StreamConfiguration effective(UUID slot) {
    var rows=db.queryForList("select c.encrypted_configuration from session s join session_stream_configuration c on c.session_id=s.id or c.program_id=s.program_id where s.id=? order by (c.session_id is not null) desc limit 1",slot);
    return rows.isEmpty()?null:decode((String)rows.get(0).get("encrypted_configuration"));
  }
  public StreamConfiguration forJoin(UUID slot) {
    var override=effective(slot);if(override!=null) return override;
    var asset=db.queryForMap("select a.provider_type,a.provider_asset_id from session s join daily_session_asset a on a.id=s.daily_asset_id where s.id=?",slot);
    return new StreamConfiguration((String)asset.get("provider_type"),true,null,null,(String)asset.get("provider_asset_id"),null,null);
  }
  public Map<String,Object> adminGet(boolean program,UUID id) {
    var rows=db.queryForList("select encrypted_configuration from session_stream_configuration where "+(program?"program_id":"session_id")+"=?",id);
    return rows.isEmpty()?Map.of("configured",false):Map.of("configured",true,"configuration",decode((String)rows.get(0).get("encrypted_configuration")));
  }
  @Transactional public void save(boolean program,UUID id,StreamConfiguration input,UUID actor,boolean initial) {
    if(program) {
      if(db.queryForList("select id from program where id=? for update",id).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Program not found");
      // Lock affected sessions too so joins cannot race a change to an inherited provider.
      db.queryForList("select id from session where program_id=? for update",id);
      if(db.queryForObject("select count(*) from session where program_id=? and starts_at<=now() and ends_at>now() and not exists(select 1 from session_stream_configuration c where c.session_id=session.id)",Integer.class,id)>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"An inherited session is already running; change its program configuration after it ends");
    } else {
      var rows=db.queryForList("select starts_at<=now() as started from session where id=? for update",id);
      if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");
      if(!initial && Boolean.TRUE.equals(rows.get(0).get("started"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stream configuration can only change before the session starts");
    }
    StreamConfiguration config=validate(input);
    String column=program?"program_id":"session_id";
    try {
      db.update("insert into session_stream_configuration(id,"+column+",provider_type,enabled,encrypted_configuration) values(?,?,?,?,?) on conflict("+column+") do update set provider_type=excluded.provider_type,enabled=excluded.enabled,encrypted_configuration=excluded.encrypted_configuration,updated_at=now()",UUID.randomUUID(),id,config.providerType(),config.enabled(),secrets.encrypt(json.writeValueAsString(config)));
    } catch(com.fasterxml.jackson.core.JsonProcessingException e) {throw new IllegalStateException("Unable to save stream configuration");}
    // Outstanding access grants must never silently switch providers.
    db.update("update playback_session set status='REVOKED' where status='ACTIVE' and session_slot_id in (select s.id from session s where "+(program?"s.program_id":"s.id")+"=?"+(program?" and not exists(select 1 from session_stream_configuration c where c.session_id=s.id)":"")+")",id);
    db.update("insert into audit_log(id,actor_id,action,entity_type,entity_id) values(?,?,?,?,?)",UUID.randomUUID(),actor,"STREAM_CONFIGURATION_UPDATED",program?"program":"session",id);
  }
  public StreamConfiguration validate(StreamConfiguration input) {
    if(input==null || !("ZOOM".equals(input.providerType()) || "YOUTUBE".equals(input.providerType()))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose Zoom or YouTube Live");
    try {
      String url=null,video=null;
      if("ZOOM".equals(input.providerType()) && (input.enabled() || (input.providerJoinUrl()!=null && !input.providerJoinUrl().isBlank()))) url=ZoomSessionJoinProvider.validatedUrl(input.providerJoinUrl());
      if("YOUTUBE".equals(input.providerType()) && (input.enabled() || (input.providerVideoId()!=null && !input.providerVideoId().isBlank()))) video=videos.get("YOUTUBE").normalizeAssetId(input.providerVideoId());
      return new StreamConfiguration(input.providerType(),input.enabled(),url,"ZOOM".equals(input.providerType())?input.providerMeetingId():null,video,"ZOOM".equals(input.providerType())?input.meetingPasscode():null,input.technicalNotes());
    } catch(IllegalArgumentException e) {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid streaming provider configuration");}
  }
  private StreamConfiguration decode(String encrypted) {
    try {return json.readValue(secrets.decrypt(encrypted),StreamConfiguration.class);} catch(Exception e) {throw new IllegalStateException("Unable to read stream configuration");}
  }
}
