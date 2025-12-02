package com.cs6650.creditcardauthorizer;

import com.cs6650.cca.model.ProcessPaymentRequest;
import com.cs6650.creditcardauthorizer.controller.CreditCardController;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CreditCardController
 *
 * Tests verify Assignment 3 requirements:
 * 1. Syntax validation for credit card format
 * 2. Proper response codes (200/402/400)
 */
class CreditCardControllerTest {

  private final CreditCardController controller = new CreditCardController();

  @Test
  void testValidCardFormat_ShouldReturnOkOrDeclined() {
    // Valid format
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCreditCardNumber("1234-5678-9012-3456");

    // Run multiple times to test randomization
    int authorizedCount = 0;
    int declinedCount = 0;
    int totalRequests = 1000;

    for (int i = 0; i < totalRequests; i++) {
      ResponseEntity<Void> response = controller.processPayment(request);
      int status = response.getStatusCode().value();

      assertTrue(status == 200 || status == 402,
          "Status should be 200 (OK) or 402 (Payment Required)");

      if (status == 200) authorizedCount++;
      if (status == 402) declinedCount++;
    }

    // Verify approximately 90% authorization rate (with tolerance)
    double authRate = (double) authorizedCount / totalRequests;
    assertTrue(authRate > 0.85 && authRate < 0.95,
        String.format("Authorization rate should be ~90%%, was %.2f%%", authRate * 100));

    System.out.printf("Authorization rate: %.2f%% (%d/%d authorized)%n",
        authRate * 100, authorizedCount, totalRequests);
  }

  @Test
  void testInvalidCardFormat_TooShort() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCreditCardNumber("1234-5678-9012");

    ResponseEntity<Void> response = controller.processPayment(request);

    assertEquals(400, response.getStatusCode().value(),
        "Should return 400 for invalid format");
  }

  @Test
  void testInvalidCardFormat_NoHyphens() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCreditCardNumber("1234567890123456");

    ResponseEntity<Void> response = controller.processPayment(request);

    assertEquals(400, response.getStatusCode().value(),
        "Should return 400 for missing hyphens");
  }

  @Test
  void testInvalidCardFormat_WrongGrouping() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCreditCardNumber("123-4567-8901-23456");

    ResponseEntity<Void> response = controller.processPayment(request);

    assertEquals(400, response.getStatusCode().value(),
        "Should return 400 for wrong grouping");
  }

  @Test
  void testInvalidCardFormat_LettersIncluded() {
    ProcessPaymentRequest request = new ProcessPaymentRequest();
    request.setCreditCardNumber("ABCD-5678-9012-3456");

    ResponseEntity<Void> response = controller.processPayment(request);

    assertEquals(400, response.getStatusCode().value(),
        "Should return 400 for non-numeric characters");
  }
}