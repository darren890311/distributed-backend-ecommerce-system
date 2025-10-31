package com.cs6650.loadtest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Checkout request model with credit card information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {

  @JsonProperty("credit_card_number")
  private String creditCardNumber;  // Format: XXXX-XXXX-XXXX-XXXX
}