package com.cs6650.productservice.controller;

import com.cs6650.productservice.api.ProductApi;
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

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProductController implements ProductApi {

  private final ProductService productService;

  @Override
  @PostMapping("/products")
  public ResponseEntity<CreateProduct201Response> createProduct(@RequestBody Product product) {
    try {
      log.info("Creating product: {}", product.getSku());

      CreateProduct201Response response = new CreateProduct201Response();

      Product savedProduct = productService.createProduct(product);

      response.setProductId(savedProduct.getProductId());

      return ResponseEntity
          .created(URI.create("/products/" + savedProduct.getProductId()))
          .body(response);

    } catch (ProductService.ServiceUnavailableException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during product creation: {}", e.getMessage());
      return ResponseEntity.status(500).build();
    }
  }

  @Override
  @GetMapping("/products/{productId}")
  public ResponseEntity<Product> getProduct(@PathVariable("productId") Integer productId) {
    try {
      log.info("Fetching product: {}", productId);

      Optional<Product> product = productService.getProduct(productId);

      if (product.isEmpty()) {
        log.warn("Product not found: {}", productId);
        return ResponseEntity.notFound().build();
      }

      return ResponseEntity.ok(product.get());

    } catch (ProductService.ServiceUnavailableException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during product fetch: {}", e.getMessage());
      return ResponseEntity.status(500).build();
    }
  }
}