package com.cs6650.shoppingcartservice.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class RabbitMQConfig {

  public static final String CHECKOUT_QUEUE = "checkoutQueue";

  @Bean
  public Queue checkoutQueue() {
    log.info("Creating queue bean: {}", CHECKOUT_QUEUE);
    return new Queue(CHECKOUT_QUEUE, true);
  }

  @Bean
  public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
    log.info("Creating RabbitAdmin bean");
    RabbitAdmin admin = new RabbitAdmin(connectionFactory);
    admin.setAutoStartup(true);
    return admin;
  }

  @Bean
  public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
    log.info("Creating RabbitTemplate bean");
    RabbitTemplate template = new RabbitTemplate(connectionFactory);
    template.setMessageConverter(new Jackson2JsonMessageConverter());
    return template;
  }
}