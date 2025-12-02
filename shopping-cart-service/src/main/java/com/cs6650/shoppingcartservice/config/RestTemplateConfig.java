package com.cs6650.shoppingcartservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
  @Bean
  public MappingJackson2HttpMessageConverter messageConverter(ObjectMapper objectMapper) {
    MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
    converter.setObjectMapper(objectMapper);
    return converter;
  }

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder, MappingJackson2HttpMessageConverter messageConverter) {
    return builder
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(15))
        .messageConverters(messageConverter)
        .build();
  }
}