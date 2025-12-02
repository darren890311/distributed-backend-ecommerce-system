package com.cs6650.productservice.service;

import com.cs6650.productservice.kvclient.KvStoreClient;
import com.cs6650.productservice.model.Product;
// 刪除對 ProductRepository 和 ProductEntity 的引入，如果它們仍然存在的話

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

  private final KvStoreClient kvStoreClient;
  private final Random random = new Random();

  private double errorRate = Double.parseDouble(
      System.getenv().getOrDefault("PRODUCT_SERVICE_ERROR_RATE", "0.1")
  );

  private boolean badMode = false;

  public static class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
      super(message);
    }
  }

  public Product createProduct(Product product) {
    log.info("Creating product: {}", product.getSku());

    if (badMode && random.nextDouble() < errorRate) {
      log.error("BAD MODE: Simulating service unavailable (error rate: {}%)", (int)(errorRate * 100));
      throw new ServiceUnavailableException("Service temporarily unavailable");
    }

    try {
      Integer productId = random.nextInt(Integer.MAX_VALUE) + 1;
      product.setProductId(productId);

      kvStoreClient.set(productId, product);

      log.info("Product created with ID: {}", productId);
      return product;
    } catch (Exception e) {
      log.error("Failed to save product to KV DB: {}", e.getMessage());
      throw new RuntimeException("Product creation failed due to DB error", e);
    }
  }

  public Optional<Product> getProduct(Integer productId) {
    log.info("Fetching product: {}", productId);

    if (badMode && random.nextDouble() < errorRate) {
      log.error("BAD MODE: Simulating service unavailable (error rate: {}%)", (int)(errorRate * 100));
      throw new ServiceUnavailableException("Service temporarily unavailable");
    }

    try {
      return kvStoreClient.get(productId);
    } catch (Exception e) {
      log.error("Failed to fetch product from KV DB: {}", e.getMessage());
      throw new RuntimeException("Product fetch failed due to DB error", e);
    }
  }

  public void setBadMode(boolean enabled) {
    this.badMode = enabled;
    log.info("Bad mode {} (error rate: {}%)",
        enabled ? "ENABLED" : "DISABLED", (int)(errorRate * 100));
  }

  public boolean isBadMode() {
    return badMode;
  }

  public void setErrorRate(double rate) {
    if (rate < 0.0 || rate > 1.0) {
      throw new IllegalArgumentException("Error rate must be between 0.0 and 1.0");
    }
    this.errorRate = rate;
    log.info("Error rate set to {}%", (int)(rate * 100));
  }

  public double getErrorRate() {
    return errorRate;
  }
}