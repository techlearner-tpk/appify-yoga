package dev.appify.scheduling;

import dev.appify.entitlement.SessionEntitlementService;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberSessionService {
  private static final DateTimeFormatter CLOCK=DateTimeFormatter.ofPattern("HH:mm");
  private final JdbcTemplate db;private final SessionEntitlementService entitlements;
  public MemberSessionService(JdbcTemplate db,SessionEntitlementService entitlements) {this.db=db;this.entitlements=entitlements;}
  public List<Map<String,Object>> today(UUID user) {return present(db.queryForList(BASE+" and s.local_date=(now() at time zone s.timezone)::date order by p.name,s.starts_at",user),user);}
  public List<Map<String,Object>> upcoming(UUID user) {return present(db.queryForList(BASE+" and s.starts_at>now()-interval '1 day' and s.starts_at<now()+interval '14 days' order by s.starts_at limit 200",user),user);}
  public Map<String,Object> one(UUID user,UUID slot) {
    var rows=db.queryForList(BASE+" and s.id=?",user,slot);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Session not found");
    return present(rows,user).get(0);
  }
  private static final String BASE="""
    select s.id,p.name as program_name,s.local_date,s.starts_at,s.ends_at,s.timezone,
           d.selected_session_slot_id,d.joined_session_slot_id,d.status as participation_status
    from session s join program p on p.id=s.program_id
    join program_enrollment e on e.program_id=p.id and e.user_id=? and e.active=true
    left join user_daily_participation d on d.user_id=e.user_id and d.program_id=s.program_id and d.local_date=s.local_date
    where s.status<>'CANCELLED'
    """;
  private List<Map<String,Object>> present(List<Map<String,Object>> rows,UUID user) {
    var result=new ArrayList<Map<String,Object>>();Instant now=Instant.now();
    for(var row:rows) {
      UUID slot=(UUID)row.get("id");ZoneId zone=ZoneId.of((String)row.get("timezone"));
      Instant start=((Timestamp)row.get("starts_at")).toInstant(),end=((Timestamp)row.get("ends_at")).toInstant();
      var response=new LinkedHashMap<String,Object>();
      response.put("slotId",slot);response.put("programName",row.get("program_name"));response.put("displayDate",((Date)row.get("local_date")).toLocalDate().toString());
      response.put("startTime",CLOCK.format(start.atZone(zone)));response.put("endTime",CLOCK.format(end.atZone(zone)));
      response.put("startsAt",start);response.put("endsAt",end);
      response.put("joinAvailability",entitlements.evaluate(user,slot,now).outcome().name());
      response.put("selected",slot.equals(row.get("selected_session_slot_id")));
      response.put("joined",slot.equals(row.get("joined_session_slot_id")));
      response.put("completed","QUALIFIED".equals(row.get("participation_status")) || "COMPLETED".equals(row.get("participation_status")));
      result.add(response);
    }
    return result;
  }
}
