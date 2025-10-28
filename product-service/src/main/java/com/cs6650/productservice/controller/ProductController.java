package com.cs6650.productservice.controller;

import com.cs6650.productservice.api.ProductApi;
import com.cs6650.productservice.entity.ProductEntity;
import com.cs6650.productservice.model.CreateProduct201Response;
import com.cs6650.productservice.model.Error;
import com.cs6650.productservice.model.Product;
import com.cs6650.productservice.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Optional;

/**
 * Product Controller
 * Implements ProductApi interface generated from OpenAPI spec
 * Manually adds endpoint mappings since generated interface doesn't have them
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProductController implements ProductApi {

  private final ProductService productService;

  /**
   * POST /products
   * Create a new product
   */
  @Override
  @PostMapping("/products")  // ← ADD THIS EXPLICITLY
  public ResponseEntity<CreateProduct201Response> createProduct(@RequestBody Product product) {
    try {
      log.info("Creating product: {}", product.getSku());

      // Ignore product_id from request - database will auto-generate
      product.setProductId(null);

      // Save to database
      ProductEntity saved = productService.createProduct(product);

      // Build response
      CreateProduct201Response response = new CreateProduct201Response();
      response.setProductId(saved.getProductId());

      // Return 201 Created with Location header
      return ResponseEntity
          .created(URI.create("/products/" + saved.getProductId()))
          .body(response);

    } catch (ProductService.ServiceUnavailableException e) {
      log.error("Service unavailable: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * GET /products/{productId}
   * Retrieve product by ID
   */
  @Override
  @GetMapping("/products/{productId}")  // ← ADD THIS EXPLICITLY
  public ResponseEntity<Product> getProduct(@PathVariable("productId") Integer productId) {
    try {
      log.info("Fetching product: {}", productId);

      Optional<ProductEntity> entity = productService.getProduct(productId);

      if (entity.isEmpty()) {
        log.warn("Product not found: {}", productId);
        return ResponseEntity.notFound().build();
      }

      // Convert entity to model
      Product product = toModel(entity.get());

      return ResponseEntity.ok(product);

    } catch (ProductService.ServiceUnavailableException e) {
      log.error("Service unavailable: {}", e.getMessage());
      throw e;
    }
  }

  /**
   * Convert JPA entity to OpenAPI model
   */
  private Product toModel(ProductEntity entity) {
    Product product = new Product();
    product.setProductId(entity.getProductId());
    product.setSku(entity.getSku());
    product.setManufacturer(entity.getManufacturer());
    product.setCategoryId(entity.getCategoryId());
    product.setWeight(entity.getWeight());
    product.setSomeOtherId(entity.getSomeOtherId());
    return product;
  }

  /**
   * Exception handler for ServiceUnavailableException
   * Returns 503 Service Unavailable
   */
  @ExceptionHandler(ProductService.ServiceUnavailableException.class)
  public ResponseEntity<Error> handleServiceUnavailable(
      ProductService.ServiceUnavailableException ex) {

    log.error("Service unavailable: {}", ex.getMessage());

    Error error = new Error();
    error.setError("SERVICE_UNAVAILABLE");
    error.setMessage(ex.getMessage());
    error.setDetails("The service is temporarily unavailable. Please try again later.");

    return ResponseEntity.status(503).body(error);
  }

  /**
   * Toggle bad mode (for testing)
   * GET /products/bad-mode?enabled=true&errorRate=0.5
   */
  @GetMapping("/products/bad-mode")
  public ResponseEntity<String> toggleBadMode(
      @RequestParam("enabled") boolean enabled,
      @RequestParam(value = "errorRate", required = false) Double errorRate) {

    // Set error rate if provided
    if (errorRate != null) {
      try {
        productService.setErrorRate(errorRate);
      } catch (IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
      }
    }

    // Enable/disable bad mode
    productService.setBadMode(enabled);

    String message = String.format(
        "Bad mode %s (%d%% of requests will return 503)",
        enabled ? "ENABLED" : "DISABLED",
        (int)(productService.getErrorRate() * 100)
    );

    log.info(message);
    return ResponseEntity.ok(message);
  }

  /**
   * Get current bad mode status
   * GET /products/bad-mode/status
   */
  @GetMapping("/products/bad-mode/status")
  public ResponseEntity<String> getBadModeStatus() {
    String status = String.format(
        "Bad mode: %s, Error rate: %d%%",
        productService.isBadMode() ? "ENABLED" : "DISABLED",
        (int)(productService.getErrorRate() * 100)
    );
    return ResponseEntity.ok(status);
  }
}