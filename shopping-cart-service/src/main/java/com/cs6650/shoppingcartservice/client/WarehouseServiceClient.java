package com.cs6650.shoppingcartservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.cs6650.shoppingcartservice.service.ShoppingCartService.InsufficientStockException;

@Service
@Slf4j
public class WarehouseServiceClient {

  private final RestTemplate restTemplate;

  @Value("${services.warehouse.url}")
  private String warehouseServiceUrl; // http://localhost:9083

  public WarehouseServiceClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  public boolean checkInventoryAndReserve(Integer productId, Integer quantity) {
    String url = String.format("%s/warehouse/reserve/%d?quantity=%d",
        warehouseServiceUrl, productId, quantity);
    try {
      restTemplate.postForEntity(url, null, Void.class);
      log.info("Warehouse reserve successful for Product: {}", productId);
      return true;
    } catch (HttpClientErrorException.NotFound e) {
      log.warn("Warehouse reserve failed: Product {} insufficient stock (404)", productId);
      throw new InsufficientStockException("Product " + productId + " stock insufficient");
    } catch (Exception e) {
      log.error("Warehouse reserve communication error: {}", e.getMessage());
      throw new RuntimeException("Warehouse service communication error", e);
    }
  }
}