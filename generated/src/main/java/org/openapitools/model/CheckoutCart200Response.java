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
 * CheckoutCart200Response
 */

@JsonTypeName("checkoutCart_200_response")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class CheckoutCart200Response {

  private Integer orderId;

  public CheckoutCart200Response orderId(Integer orderId) {
    this.orderId = orderId;
    return this;
  }

  /**
   * Unique identifier for the created order
   * @return orderId
  */
  
  @Schema(name = "order_id", description = "Unique identifier for the created order", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("order_id")
  public Integer getOrderId() {
    return orderId;
  }

  public void setOrderId(Integer orderId) {
    this.orderId = orderId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CheckoutCart200Response checkoutCart200Response = (CheckoutCart200Response) o;
    return Objects.equals(this.orderId, checkoutCart200Response.orderId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(orderId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CheckoutCart200Response {\n");
    sb.append("    orderId: ").append(toIndentedString(orderId)).append("\n");
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

