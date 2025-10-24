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
 * CreateShoppingCart201Response
 */

@JsonTypeName("createShoppingCart_201_response")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class CreateShoppingCart201Response {

  private Integer shoppingCartId;

  public CreateShoppingCart201Response shoppingCartId(Integer shoppingCartId) {
    this.shoppingCartId = shoppingCartId;
    return this;
  }

  /**
   * Unique identifier for the created shopping cart
   * @return shoppingCartId
  */
  
  @Schema(name = "shopping_cart_id", description = "Unique identifier for the created shopping cart", requiredMode = Schema.RequiredMode.NOT_REQUIRED)
  @JsonProperty("shopping_cart_id")
  public Integer getShoppingCartId() {
    return shoppingCartId;
  }

  public void setShoppingCartId(Integer shoppingCartId) {
    this.shoppingCartId = shoppingCartId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CreateShoppingCart201Response createShoppingCart201Response = (CreateShoppingCart201Response) o;
    return Objects.equals(this.shoppingCartId, createShoppingCart201Response.shoppingCartId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(shoppingCartId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CreateShoppingCart201Response {\n");
    sb.append("    shoppingCartId: ").append(toIndentedString(shoppingCartId)).append("\n");
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

