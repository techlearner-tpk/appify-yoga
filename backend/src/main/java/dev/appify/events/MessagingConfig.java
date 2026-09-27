package dev.appify.events;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.retry.interceptor.RetryInterceptorBuilder;

@Configuration
public class MessagingConfig {
  public static final String EXCHANGE="wellness.events", QUEUE="wellness.events.worker", DLQ="wellness.events.dead";
  @Bean DirectExchange exchange() {return new DirectExchange(EXCHANGE,true,false);}
  @Bean Queue workerQueue() {return QueueBuilder.durable(QUEUE).withArgument("x-dead-letter-exchange",EXCHANGE).withArgument("x-dead-letter-routing-key","dead").build();}
  @Bean Queue deadQueue() {return QueueBuilder.durable(DLQ).build();}
  @Bean Binding workerBinding() {return BindingBuilder.bind(workerQueue()).to(exchange()).with("work");}
  @Bean Binding deadBinding() {return BindingBuilder.bind(deadQueue()).to(exchange()).with("dead");}
  @Bean Jackson2JsonMessageConverter messageConverter() {return new Jackson2JsonMessageConverter();}
  @Bean SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,Jackson2JsonMessageConverter converter) {
    var factory=new SimpleRabbitListenerContainerFactory(); factory.setConnectionFactory(connectionFactory);factory.setMessageConverter(converter);
    factory.setDefaultRequeueRejected(false);
    factory.setAdviceChain(RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(1000,2,5000).build());
    return factory;
  }
}
