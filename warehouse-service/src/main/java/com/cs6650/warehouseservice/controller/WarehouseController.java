package com.cs6650.warehouseservice.controller;

import com.cs6650.warehouseservice.service.WarehouseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/warehouse")
public class WarehouseController {

  private final WarehouseService warehouseService;
  private final Random random = new Random();

  private void addBusinessLogicDelay() {
    try {
      long delay = 100 + random.nextInt(901);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @PostMapping("/reserve/{productId}")
  public ResponseEntity<Void> reserve(
      @PathVariable("productId") Integer productId,
      @RequestParam("quantity") Integer quantity) {
    addBusinessLogicDelay();
    if (random.nextDouble() < 0.10) {
      log.warn("RESERVE FAILED: Product {} (Quantity {}) - Insufficient inventory (Simulated)", productId, quantity);
      return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    log.info("RESERVE SUCCESS: Product {} (Quantity {}) - Inventory reserved", productId, quantity);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/ship/{productId}")
  public ResponseEntity<Void> ship(
      @PathVariable("productId") Integer productId,
      @RequestParam("quantity") Integer quantity) {
    addBusinessLogicDelay();

    log.info("SHIP SUCCESS: Product {} (Quantity {}) - Shipment process initiated (100% success)", productId, quantity);
    return ResponseEntity.ok().build();
  }
  @GetMapping("/health")
  public String healthCheck() {
    return "Warehouse Service is running";
  }

  @GetMapping("/stats")
  public String getStats() {
    return "Total Orders: " + warehouseService.getTotalOrders()
        + " | Tracked Products: " + warehouseService.getProductQuantities().size();
  }
}