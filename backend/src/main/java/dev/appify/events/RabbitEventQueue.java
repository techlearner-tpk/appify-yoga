package dev.appify.events;

import java.util.Map;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitEventQueue implements EventQueue {
  private final RabbitTemplate rabbit;
  public RabbitEventQueue(RabbitTemplate rabbit) {this.rabbit=rabbit;}
  @Override public void publish(UUID id,String type,String payload) {rabbit.convertAndSend(MessagingConfig.EXCHANGE,"work",Map.of("eventId",id.toString(),"eventType",type,"payload",payload));}
}
