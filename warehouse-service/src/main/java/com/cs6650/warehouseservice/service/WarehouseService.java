package com.cs6650.warehouseservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;

@Service
public class WarehouseService {

  private final AtomicInteger totalOrders = new AtomicInteger(0);
  private final Map<Integer, AtomicInteger> productQuantities = new ConcurrentHashMap<>();

  public void recordOrder(int orderId, JsonNode products) {
    totalOrders.incrementAndGet();

    for (JsonNode product : products) {
      int productId = product.get("product_id").asInt();
      int quantity = product.get("quantity").asInt();

      productQuantities
          .computeIfAbsent(productId, k -> new AtomicInteger(0))
          .addAndGet(quantity);
    }

    System.out.println("Processed order " + orderId +
        " | Total Orders: " + totalOrders.get());
    System.out.println("Current Product Totals:");
    productQuantities.forEach((id, totalQty) ->
        System.out.println(" - Product " + id + ": " + totalQty.get()));
  }

  @PreDestroy
  public void onShutdown() {
    System.out.println("=== Warehouse shutting down ===");
    System.out.println("Total Orders: " + totalOrders.get());
  }

  public int getTotalOrders() {
    return totalOrders.get();
  }

  public Map<Integer, AtomicInteger> getProductQuantities() {
    return productQuantities;
  }
}
