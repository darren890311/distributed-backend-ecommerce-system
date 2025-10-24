package com.cs6650.warehouseservice.controller;

import com.cs6650.warehouseservice.service.WarehouseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WarehouseController {

  private final WarehouseService warehouseService;

  public WarehouseController(WarehouseService warehouseService) {
    this.warehouseService = warehouseService;
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
