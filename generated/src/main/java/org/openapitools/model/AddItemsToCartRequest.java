package org.openapitools.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import javax.annotation.Generated;

/**
 * AddItemsToCartRequest
 */

@JsonTypeName("addItemsToCart_request")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class AddItemsToCartRequest {

  private Integer productId;

  private Integer quantity;

  public AddItemsToCartRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public AddItemsToCartRequest(Integer productId, Integer quantity) {
    this.productId = productId;
    this.quantity = quantity;
  }

  public AddItemsToCartRequest productId(Integer productId) {
    this.productId = productId;
    return this;
  }

  /**
   * Unique identifier for the product
   * minimum: 1
   * @return productId
  */
  @NotNull @Min(1) 
  @Schema(name = "product_id", description = "Unique identifier for the product", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("product_id")
  public Integer getProductId() {
    return productId;
  }

  public void setProductId(Integer productId) {
    this.productId = productId;
  }

  public AddItemsToCartRequest quantity(Integer quantity) {
    this.quantity = quantity;
    return this;
  }

  /**
   * Number of items to add
   * minimum: 1
   * @return quantity
  */
  @NotNull @Min(1) 
  @Schema(name = "quantity", description = "Number of items to add", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("quantity")
  public Integer getQuantity() {
    return quantity;
  }

  public void setQuantity(Integer quantity) {
    this.quantity = quantity;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddItemsToCartRequest addItemsToCartRequest = (AddItemsToCartRequest) o;
    return Objects.equals(this.productId, addItemsToCartRequest.productId) &&
        Objects.equals(this.quantity, addItemsToCartRequest.quantity);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, quantity);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AddItemsToCartRequest {\n");
    sb.append("    productId: ").append(toIndentedString(productId)).append("\n");
    sb.append("    quantity: ").append(toIndentedString(quantity)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }
}

