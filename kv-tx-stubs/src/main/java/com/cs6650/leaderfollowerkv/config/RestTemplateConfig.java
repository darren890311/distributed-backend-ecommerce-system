package com.cs6650.leaderfollowerkv.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configures RestTemplate for making HTTP requests to other nodes
 */
@Configuration
public class RestTemplateConfig {

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(5))  // Timeout for connection
        .setReadTimeout(Duration.ofSeconds(10))     // Timeout for response
        .build();
  }
}