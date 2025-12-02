package com.cs6650.shoppingcartservice.kvclient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
public class KvStoreClient {

  @Value("${kvstore.leader.url}")
  private String leaderUrl;

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;
  private final Random random = new Random();

  public KvStoreClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
  }

  private void addBusinessLogicDelay() {
    try {
      long delay = 100 + random.nextInt(901);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  public void setShoppingCart(Integer key, ShoppingCart cart) throws Exception {
    addBusinessLogicDelay();

    String valueJson = this.objectMapper.writeValueAsString(cart);
    String encodedValue = URLEncoder.encode(valueJson, StandardCharsets.UTF_8.toString());

    String url = String.format("%s/api/kv/set?key=%d&value=%s",
        leaderUrl, key, encodedValue);

    try {
      restTemplate.postForEntity(url, null, Void.class);
      log.info("KV SET successful for cart key: {}", key);
    } catch (Exception e) {
      log.error("KV DB SET failed for cart key {}: {}", key, e.getMessage());
      throw new RuntimeException("KV DB SET failed", e);
    }
  }
  public Optional<ShoppingCart> getShoppingCart(Integer key) throws Exception {
    addBusinessLogicDelay();

    String url = String.format("%s/api/kv/get?key=%d", leaderUrl, key);

    try {
      ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {

        String encodedValue = (String) response.getBody().get("value");
        if (encodedValue == null) {
          throw new RuntimeException("KV DB response missing 'value' field.");
        }

        String decodedValue = URLDecoder.decode(encodedValue, StandardCharsets.UTF_8.toString());

        ShoppingCart cart = this.objectMapper.readValue(decodedValue, ShoppingCart.class);
        return Optional.of(cart);
      }
    } catch (HttpClientErrorException.NotFound e) {
      return Optional.empty();
    } catch (Exception e) {
      log.error("KV DB GET communication failed for key {}: {}", key, e.getMessage());
      throw new RuntimeException("KV DB GET failed", e);
    }
    return Optional.empty();
  }

  private void callTransactionStub(String endpoint, Integer cartId) {
    String url = leaderUrl + endpoint;
    try {
      restTemplate.postForEntity(url, null, Void.class);
      log.info("--- [KV DB] SUCCESS: {} for Cart {} ---", endpoint, cartId);
    } catch (Exception e) {
      log.error("Failed to call transaction stub {}: {}", endpoint, e.getMessage());
    }
  }

  public void beginTransaction(Integer cartId) {
    callTransactionStub("/api/kv/transaction/begin", cartId);
  }

  public void endTransaction(Integer cartId) {
    callTransactionStub("/api/kv/transaction/end", cartId);
  }

  public void abortTransaction(Integer cartId) {
    callTransactionStub("/api/kv/transaction/abort", cartId);
  }
}