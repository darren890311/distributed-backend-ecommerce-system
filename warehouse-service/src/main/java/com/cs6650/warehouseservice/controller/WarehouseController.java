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

  // NOTE: /reserve endpoint removed per Assignment 5 requirements
  // - Inventory reservation no longer used
  // - Failures now come from credit card service (10% decline rate)
  // - Ship is handled via RabbitMQ (fire-and-forget) - see WarehouseConsumer

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