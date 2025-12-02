package com.cs6650.warehouseservice.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

  public static final String CHECKOUT_QUEUE = "checkoutQueue";

  @Bean
  public Queue checkoutQueue() {
    return new Queue(CHECKOUT_QUEUE, true);
  }
}