package com.cs6650.shoppingcartservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Message sent to RabbitMQ for warehouse processing
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage {

  @JsonProperty("order_id")
  private Integer orderId;
  private List<ProductItem> products;

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ProductItem {
    @JsonProperty("product_id")
    private Integer productId;
    private Integer quantity;
  }
}