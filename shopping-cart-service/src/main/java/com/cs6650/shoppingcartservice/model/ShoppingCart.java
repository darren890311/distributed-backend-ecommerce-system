package com.cs6650.shoppingcartservice.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart {

  private Integer shoppingCartId;
  private Integer customerId;
  private LocalDateTime createdAt = LocalDateTime.now();
  private String status;
  private List<CartItem> items = new ArrayList<>();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CartItem {
    private Integer productId;
    private Integer quantity;
  }
}