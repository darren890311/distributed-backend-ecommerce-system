package com.cs6650.productservice.service;

import com.cs6650.productservice.entity.ProductEntity;
import com.cs6650.productservice.model.Product;
import com.cs6650.productservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Random;

/**
 * Business logic for Product operations
 * Includes "bad" version that returns 503 errors 10% of the time
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductService {

  private final ProductRepository productRepository;
  private final Random random = new Random();

  // Error rate can be configured via environment variable
  // Default: 0.1 (10%)
  // For 50% failure simulation, set PRODUCT_SERVICE_ERROR_RATE=0.5
  private double errorRate = Double.parseDouble(
      System.getenv().getOrDefault("PRODUCT_SERVICE_ERROR_RATE", "0.1")
  );

  private boolean badMode = false;

  /**
   * Create a new product
   */
  public ProductEntity createProduct(Product product) {
    log.info("Creating product: {}", product.getSku());

    // Simulate bad behavior if enabled
    if (badMode && random.nextDouble() < errorRate) {
      log.error("BAD MODE: Simulating service unavailable (error rate: {}%)", (int)(errorRate * 100));
      throw new ServiceUnavailableException("Service temporarily unavailable");
    }

    ProductEntity entity = new ProductEntity();
    entity.setSku(product.getSku());
    entity.setManufacturer(product.getManufacturer());
    entity.setCategoryId(product.getCategoryId());
    entity.setWeight(product.getWeight());
    entity.setSomeOtherId(product.getSomeOtherId());

    ProductEntity saved = productRepository.save(entity);
    log.info("Product created with ID: {}", saved.getProductId());

    return saved;
  }

  /**
   * Get product by ID
   */
  public Optional<ProductEntity> getProduct(Integer productId) {
    log.info("Fetching product: {}", productId);

    // Simulate bad behavior if enabled
    if (badMode && random.nextDouble() < errorRate) {
      log.error("BAD MODE: Simulating service unavailable (error rate: {}%)", (int)(errorRate * 100));
      throw new ServiceUnavailableException("Service temporarily unavailable");
    }

    return productRepository.findById(productId);
  }

  /**
   * Enable/disable bad mode
   */
  public void setBadMode(boolean enabled) {
    this.badMode = enabled;
    log.info("Bad mode {} (error rate: {}%)",
        enabled ? "ENABLED" : "DISABLED", (int)(errorRate * 100));
  }

  public boolean isBadMode() {
    return badMode;
  }

  /**
   * Set custom error rate (0.0 to 1.0)
   */
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

  /**
   * Custom exception for 503 errors
   */
  public static class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
      super(message);
    }
  }
}