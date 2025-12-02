package com.cs6650.creditcardauthorizer.controller;

import com.cs6650.cca.api.PaymentsApi;
import com.cs6650.cca.model.ProcessPaymentRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;
import java.util.regex.Pattern;

@Slf4j
@RestController
public class CreditCardController implements PaymentsApi {

  private final Random random = new Random();
  private static final double AUTHORIZATION_RATE = 0.9;
  private static final Pattern CARD_PATTERN =
      Pattern.compile("^[0-9]{4}-[0-9]{4}-[0-9]{4}-[0-9]{4}$");

  private void addBusinessLogicDelay() {
    try {
      long delay = 100 + random.nextInt(901);
      Thread.sleep(delay);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Process credit card payment authorization.
   */
  @Override
  public ResponseEntity<Void> processPayment(ProcessPaymentRequest processPaymentRequest) {

    addBusinessLogicDelay();

    String cardNumber = processPaymentRequest.getCreditCardNumber();

    log.info("Processing authorization for card: {}****", cardNumber.substring(0, 9));

    if (!isValidCardFormat(cardNumber)) {
      log.error("Invalid card format detected: {}",
          cardNumber.replaceAll("\\d", "*"));
      return ResponseEntity.badRequest().build(); // 400
    }

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
   */
  private boolean isValidCardFormat(String cardNumber) {
    if (cardNumber == null || cardNumber.isEmpty()) {
      return false;
    }
    return CARD_PATTERN.matcher(cardNumber).matches();
  }
}