package dev.appify.notifications;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import dev.appify.entitlement.JoinLinkService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
class ProviderReminderTest {
  @Test void remindersContainOnlyApplicationJoinUrls() {
    var db=mock(JdbcTemplate.class);var links=mock(JoinLinkService.class);UUID user=UUID.randomUUID(),slot=UUID.randomUUID();
    when(db.queryForList(anyString(),anyInt(),anyInt())).thenReturn(List.of(Map.of("user_id",user,"session_id",slot,"name","Yoga Everyday")));
    when(db.queryForObject(anyString(),eq(Integer.class),any())).thenReturn(0);when(links.create(user,slot)).thenReturn("opaque-random-token");
    new ReminderScheduler(db,links,"scheduler","https://ourapp.example.com").reminders();
    var body=ArgumentCaptor.forClass(String.class);
    verify(db,times(2)).update(anyString(),any(UUID.class),eq(user),body.capture(),anyString());
    for(String message:body.getAllValues()) assertThat(message).contains("https://ourapp.example.com/j/opaque-random-token").doesNotContain("zoom.us","youtube.com",slot.toString(),user.toString());
  }
}
