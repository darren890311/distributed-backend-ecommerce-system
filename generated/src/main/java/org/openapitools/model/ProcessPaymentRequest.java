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
 * ProcessPaymentRequest
 */

@JsonTypeName("processPayment_request")
@Generated(value = "org.openapitools.codegen.languages.SpringCodegen", date = "2025-10-23T02:52:50.284826-07:00[America/Los_Angeles]")
public class ProcessPaymentRequest {

  private String creditCardNumber;

  public ProcessPaymentRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ProcessPaymentRequest(String creditCardNumber) {
    this.creditCardNumber = creditCardNumber;
  }

  public ProcessPaymentRequest creditCardNumber(String creditCardNumber) {
    this.creditCardNumber = creditCardNumber;
    return this;
  }

  /**
   * Credit card number as string as groups of 4 digits with dashes (minus sign)
   * @return creditCardNumber
  */
  @NotNull @Pattern(regexp = "^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$") 
  @Schema(name = "credit_card_number", example = "1234-5678-1234-5678", description = "Credit card number as string as groups of 4 digits with dashes (minus sign)", requiredMode = Schema.RequiredMode.REQUIRED)
  @JsonProperty("credit_card_number")
  public String getCreditCardNumber() {
    return creditCardNumber;
  }

  public void setCreditCardNumber(String creditCardNumber) {
    this.creditCardNumber = creditCardNumber;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProcessPaymentRequest processPaymentRequest = (ProcessPaymentRequest) o;
    return Objects.equals(this.creditCardNumber, processPaymentRequest.creditCardNumber);
  }

  @Override
  public int hashCode() {
    return Objects.hash(creditCardNumber);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ProcessPaymentRequest {\n");
    sb.append("    creditCardNumber: ").append(toIndentedString(creditCardNumber)).append("\n");
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

