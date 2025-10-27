package com.cs6650.loadtest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Shopping cart response model.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCart {

  @JsonProperty("shopping_cart_id")
  private Integer shoppingCartId;
}