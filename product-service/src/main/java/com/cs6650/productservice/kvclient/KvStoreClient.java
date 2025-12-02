package com.cs6650.productservice.kvclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cs6650.productservice.model.Product;
import java.net.URLDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
public class KvStoreClient {

  @Value("${kvstore.leader.url}")
  private String leaderUrl;

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Random random = new Random();

  public KvStoreClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  private void addBusinessLogicDelay() {
    try {
      long delay = 100 + random.nextInt(901);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void set(Integer key, Product product) throws Exception {
    addBusinessLogicDelay();

    String valueJson = objectMapper.writeValueAsString(product);
    String encodedValue = URLEncoder.encode(valueJson, StandardCharsets.UTF_8.toString());

    String url = String.format("%s/api/kv/set?key=%d&value=%s",
        leaderUrl, key, encodedValue);

    try {
      restTemplate.postForEntity(url, null, String.class);
      log.info("KV SET successful for key: {}", key);
    } catch (Exception e) {
      log.error("KV DB SET failed for key {}: {}", key, e.getMessage());
      throw new RuntimeException("KV DB SET failed", e);
    }
  }

  public Optional<Product> get(Integer key) throws Exception {
    addBusinessLogicDelay();

    String url = String.format("%s/api/kv/get?key=%d", leaderUrl, key);

    try {
      ResponseEntity<VersionedValue> response =
          restTemplate.getForEntity(url, VersionedValue.class);

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        String encodedValue = response.getBody().getValue();

        String decodedValue = URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.toString());

        Product product = objectMapper.readValue(decodedValue, Product.class);
        return Optional.of(product);
      }
    } catch (ResponseStatusException e) {
    } catch (Exception e) {
    }
    return Optional.empty();
  }
}