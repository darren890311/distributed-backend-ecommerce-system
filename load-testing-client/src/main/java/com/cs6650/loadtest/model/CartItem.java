package com.cs6650.loadtest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for adding items to shopping cart.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {

  @JsonProperty("product_id")
  private Integer productId;

  @JsonProperty("quantity")
  private Integer quantity;
}