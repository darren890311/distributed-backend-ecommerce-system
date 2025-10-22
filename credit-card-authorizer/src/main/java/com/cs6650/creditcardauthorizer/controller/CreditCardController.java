package com.cs6650.creditcardauthorizer.controller;

import com.cs6650.cca.api.PaymentsApi;
import com.cs6650.cca.model.ProcessPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

/**
 * Implementation of PaymentsApi (generated from OpenAPI spec).
 *
 * This class provides the actual business logic for credit card authorization.
 * The interface contract is generated from openapi.yaml, ensuring we match
 * the API specification exactly.
 *
 * Assignment Requirements:
 * - Validates card format: XXXX-XXXX-XXXX-XXXX (handled by generated model)
 * - 90% authorization rate (200 OK)
 * - 10% decline rate (402 Payment Required)
 * - Returns 400 for invalid format (handled by Spring validation)
 */
@Slf4j
@RestController
public class CreditCardController implements PaymentsApi {

  private final Random random = new Random();
  private static final double AUTHORIZATION_RATE = 0.9;

  /**
   * Implements POST /credit-card-authorizer/authorize
   *
   * This method is defined in the PaymentsApi interface, which was
   * generated from the OpenAPI specification. By implementing this
   * interface, we guarantee our code matches the API contract.
   *
   * @param processPaymentRequest Request with validated credit_card_number
   * @return ResponseEntity with status:
   *         - 200 OK: Payment authorized (90% of requests)
   *         - 402 Payment Required: Payment declined (10% of requests)
   *         - 400 Bad Request: Invalid card format (automatic via validation)
   */
  @Override
  public ResponseEntity<Void> processPayment(ProcessPaymentRequest processPaymentRequest) {
    String cardNumber = processPaymentRequest.getCreditCardNumber();

    // Log request (mask card number for security - only show first 9 chars)
    log.info("Authorization request for card: {}****", cardNumber.substring(0, 9));

    // Assignment requirement: Simulate authorization with 90% success rate
    if (random.nextDouble() < AUTHORIZATION_RATE) {
      log.info("Payment AUTHORIZED");
      return ResponseEntity.ok().build();  // 200 OK
    } else {
      log.warn("Payment DECLINED");
      return ResponseEntity.status(402).build();  // 402 Payment Required
    }
  }
}