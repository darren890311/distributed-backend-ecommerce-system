package com.cs6650.loadtest.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates fake credit card numbers for testing.
 * Format: XXXX-XXXX-XXXX-XXXX (4 groups of 4 digits with dashes)
 *
 * IMPORTANT: Only generates fake numbers - never use real credit cards!
 */
public class CreditCardGenerator {

  /**
   * Generate a fake credit card number.
   * Uses ThreadLocalRandom for thread safety.
   */
  public static String generateFakeCreditCard() {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    StringBuilder cardNumber = new StringBuilder();
    for (int i = 0; i < 4; i++) {
      if (i > 0) {
        cardNumber.append("-");
      }
      // Generate 4 random digits
      cardNumber.append(String.format("%04d", random.nextInt(10000)));
    }

    return cardNumber.toString();
  }

  /**
   * Validate credit card number format.
   * Must match: XXXX-XXXX-XXXX-XXXX
   */
  public static boolean isValidFormat(String cardNumber) {
    if (cardNumber == null) {
      return false;
    }

    // Regex: 4 digits, dash, 4 digits, dash, 4 digits, dash, 4 digits
    return cardNumber.matches("\\d{4}-\\d{4}-\\d{4}-\\d{4}");
  }
}