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
 * CreateShoppingCartRequest
 */

@JsonTypeName("createShoppingCart_request")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class CreateShoppingCartRequest {

  private Integer customerId;

  public CreateShoppingCartRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CreateShoppingCartRequest(Integer customerId) {
    this.customerId = customerId;
  }

  public CreateShoppingCartRequest customerId(Integer customerId) {
    this.customerId = customerId;
    return this;
  }

  /**
   * Unique identifier for the customer
   * minimum: 1
   * @return customerId
  */
  @NotNull @Min(1) 
  @Schema(name = "customer_id", description = "Unique identifier for the customer", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("customer_id")
  public Integer getCustomerId() {
    return customerId;
  }

  public void setCustomerId(Integer customerId) {
    this.customerId = customerId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateShoppingCartRequest createShoppingCartRequest = (CreateShoppingCartRequest) o;
    return Objects.equals(this.customerId, createShoppingCartRequest.customerId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(customerId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateShoppingCartRequest {\n");
    sb.append("    customerId: ").append(toIndentedString(customerId)).append("\n");
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

