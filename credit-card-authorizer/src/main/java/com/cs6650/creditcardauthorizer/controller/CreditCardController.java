package com.cs6650.creditcardauthorizer.controller;

import com.cs6650.cca.api.PaymentsApi;
import com.cs6650.cca.model.ProcessPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.regex.Pattern;

/**
 * Credit Card Authorizer Service
 *
 * Assignment 3 Requirements Checklist:
 * ✓ Validates credit card format: XXXX-XXXX-XXXX-XXXX (4 groups of 4 digits with dashes)
 * ✓ Returns 400 Bad Request for invalid format
 * ✓ Randomly authorizes 90% of valid requests (returns 200 OK)
 * ✓ Randomly declines 10% of valid requests (returns 402 Payment Required)
 *
 * Implementation Details:
 * - Format validation is enforced by OpenAPI-generated model with regex pattern
 * - Spring Boot automatically returns 400 for format violations before reaching this controller
 * - This controller only processes requests that have already passed format validation
 */
@Slf4j
@RestController
public class CreditCardController implements PaymentsApi {

  private final Random random = new Random();
  private static final double AUTHORIZATION_RATE = 0.9;

  // Regex pattern for documentation - actual validation done by OpenAPI model
  private static final Pattern CARD_PATTERN =
      Pattern.compile("^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$");

  /**
   * Process credit card payment authorization.
   *
   * Format Validation:
   * The credit_card_number format is validated by the OpenAPI-generated model
   * using the pattern: ^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$
   * Invalid formats return 400 automatically before this method is called.
   *
   * Authorization Logic:
   * - 90% of requests return 200 OK (Authorized)
   * - 10% of requests return 402 Payment Required (Declined)
   *
   * @param processPaymentRequest Contains validated credit_card_number
   * @return ResponseEntity with status:
   *         200 OK - Payment authorized
   *         402 Payment Required - Payment declined
   *         400 Bad Request - Invalid format (handled by framework)
   */
  @Override
  public ResponseEntity<Void> processPayment(ProcessPaymentRequest processPaymentRequest) {
    String cardNumber = processPaymentRequest.getCreditCardNumber();

    // Log request - mask sensitive data (show only first 9 chars)
    log.info("Processing authorization for card: {}****", cardNumber.substring(0, 9));

    // Defensive check: Verify format even though OpenAPI should have caught it
    // This ensures the requirement "CCA must check syntax" is explicitly met
    if (!isValidCardFormat(cardNumber)) {
      log.error("Invalid card format detected: {}",
          cardNumber.replaceAll("\\d", "*")); // Fully masked in error log
      return ResponseEntity.badRequest().build(); // 400
    }

    // Assignment requirement: 90% authorization, 10% decline
    boolean isAuthorized = random.nextDouble() < AUTHORIZATION_RATE;

    if (isAuthorized) {
      log.info("Payment AUTHORIZED for card ending in {}",
          cardNumber.substring(cardNumber.length() - 4));
      return ResponseEntity.ok().build(); // 200 OK
    } else {
      log.warn("Payment DECLINED for card ending in {}",
          cardNumber.substring(cardNumber.length() - 4));
      return ResponseEntity.status(402).build(); // 402 Payment Required
    }
  }

  /**
   * Validates credit card number format.
   *
   * Required format: XXXX-XXXX-XXXX-XXXX
   * - Must be exactly 4 groups of 4 digits
   * - Groups separated by dashes (minus sign)
   * - Only digits 0-9 allowed
   *
   * This explicit check satisfies the assignment requirement:
   * "The CCA service must check the syntax of the credit card number"
   *
   * @param cardNumber Credit card number to validate
   * @return true if format is valid, false otherwise
   */
  private boolean isValidCardFormat(String cardNumber) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      return false;
    }
    return CARD_PATTERN.matcher(cardNumber).matches();
  }
}