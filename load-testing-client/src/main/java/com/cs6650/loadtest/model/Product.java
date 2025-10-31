package com.cs6650.loadtest.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Product data model matching the API specification.
 * Server auto-generates product_id, so it's optional in requests.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product {

  @JsonProperty("product_id")
  private Integer productId;  // Server-generated, optional in POST

  @JsonProperty("sku")
  private String sku;  // 10 characters from {A-Z0-9}

  @JsonProperty("manufacturer")
  private String manufacturer;

  @JsonProperty("category_id")
  private Integer categoryId;  // 1 to 100,000

  @JsonProperty("weight")
  private Integer weight;  // In grams

  @JsonProperty("some_other_id")
  private Integer someOtherId;
}