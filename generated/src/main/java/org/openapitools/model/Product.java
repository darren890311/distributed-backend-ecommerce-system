package org.openapitools.model;

import java.net.URI;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.openapitools.jackson.nullable.JsonNullable;
import java.time.OffsetDateTime;
import javax.validation.Valid;
import javax.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;


import java.util.*;
import javax.annotation.Generated;

/**
 * Product
 */

@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class Product {

  private Integer productId;

  private String sku;

  private String manufacturer;

  private Integer categoryId;

  private Integer weight;

  private Integer someOtherId;

  public Product() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Product(Integer productId, String sku, String manufacturer, Integer categoryId, Integer weight, Integer someOtherId) {
    this.productId = productId;
    this.sku = sku;
    this.manufacturer = manufacturer;
    this.categoryId = categoryId;
    this.weight = weight;
    this.someOtherId = someOtherId;
  }

  public Product productId(Integer productId) {
    this.productId = productId;
    return this;
  }

  /**
   * Unique identifier for the product
   * minimum: 1
   * @return productId
  */
  @NotNull @Min(1) 
  @Schema(name = "product_id", example = "12345", description = "Unique identifier for the product", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("product_id")
  public Integer getProductId() {
    return productId;
  }

  public void setProductId(Integer productId) {
    this.productId = productId;
  }

  public Product sku(String sku) {
    this.sku = sku;
    return this;
  }

  /**
   * Stock Keeping Unit - unique product code. 10 characters from the set {A-Z0-9}
   * @return sku
  */
  @NotNull @Size(min = 1, max = 100) 
  @Schema(name = "sku", example = "ABC123XYZ", description = "Stock Keeping Unit - unique product code. 10 characters from the set {A-Z0-9}", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("sku")
  public String getSku() {
    return sku;
  }

  public void setSku(String sku) {
    this.sku = sku;
  }

  public Product manufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
    return this;
  }

  /**
   * Product manufacturer name
   * @return manufacturer
  */
  @NotNull @Size(min = 1, max = 200) 
  @Schema(name = "manufacturer", example = "Acme Corporation", description = "Product manufacturer name", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("manufacturer")
  public String getManufacturer() {
    return manufacturer;
  }

  public void setManufacturer(String manufacturer) {
    this.manufacturer = manufacturer;
  }

  public Product categoryId(Integer categoryId) {
    this.categoryId = categoryId;
    return this;
  }

  /**
   * Product category identifier
   * minimum: 1
   * @return categoryId
  */
  @NotNull @Min(1) 
  @Schema(name = "category_id", example = "456", description = "Product category identifier", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("category_id")
  public Integer getCategoryId() {
    return categoryId;
  }

  public void setCategoryId(Integer categoryId) {
    this.categoryId = categoryId;
  }

  public Product weight(Integer weight) {
    this.weight = weight;
    return this;
  }

  /**
   * Product weight in grams
   * minimum: 0
   * @return weight
  */
  @NotNull @Min(0) 
  @Schema(name = "weight", example = "1250", description = "Product weight in grams", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("weight")
  public Integer getWeight() {
    return weight;
  }

  public void setWeight(Integer weight) {
    this.weight = weight;
  }

  public Product someOtherId(Integer someOtherId) {
    this.someOtherId = someOtherId;
    return this;
  }

  /**
   * Additional identifier for product
   * minimum: 1
   * @return someOtherId
  */
  @NotNull @Min(1) 
  @Schema(name = "some_other_id", example = "789", description = "Additional identifier for product", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("some_other_id")
  public Integer getSomeOtherId() {
    return someOtherId;
  }

  public void setSomeOtherId(Integer someOtherId) {
    this.someOtherId = someOtherId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Product product = (Product) o;
    return Objects.equals(this.productId, product.productId) &&
        Objects.equals(this.sku, product.sku) &&
        Objects.equals(this.manufacturer, product.manufacturer) &&
        Objects.equals(this.categoryId, product.categoryId) &&
        Objects.equals(this.weight, product.weight) &&
        Objects.equals(this.someOtherId, product.someOtherId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(productId, sku, manufacturer, categoryId, weight, someOtherId);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Product {\n");
    sb.append("    productId: ").append(toIndentedString(productId)).append("\n");
    sb.append("    sku: ").append(toIndentedString(sku)).append("\n");
    sb.append("    manufacturer: ").append(toIndentedString(manufacturer)).append("\n");
    sb.append("    categoryId: ").append(toIndentedString(categoryId)).append("\n");
    sb.append("    weight: ").append(toIndentedString(weight)).append("\n");
    sb.append("    someOtherId: ").append(toIndentedString(someOtherId)).append("\n");
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

