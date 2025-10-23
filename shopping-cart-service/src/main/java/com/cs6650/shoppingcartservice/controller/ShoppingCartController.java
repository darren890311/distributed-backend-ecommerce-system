package com.cs6650.shoppingcartservice.controller;

import com.cs6650.shoppingcart.api.ShoppingCartApi;
import com.cs6650.shoppingcart.model.*;
import com.cs6650.shoppingcartservice.entity.ShoppingCartEntity;
import com.cs6650.shoppingcartservice.service.ShoppingCartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Shopping Cart Controller
 * Implements ShoppingCartApi interface generated from OpenAPI spec
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ShoppingCartController implements ShoppingCartApi {

  private final ShoppingCartService shoppingCartService;

  @Override
  @PostMapping("/shopping-cart")
  public ResponseEntity<CreateShoppingCart201Response> createShoppingCart(
      @RequestBody CreateShoppingCartRequest request) {

    log.info("Creating shopping cart for customer: {}", request.getCustomerId());

    ShoppingCartEntity cart = shoppingCartService.createCart(request.getCustomerId());

    CreateShoppingCart201Response response = new CreateShoppingCart201Response();
    response.setShoppingCartId(cart.getShoppingCartId());

    return ResponseEntity
        .created(URI.create("/shopping-carts/" + cart.getShoppingCartId()))
        .body(response);
  }

  @Override
  @PostMapping("/shopping-carts/{shoppingCartId}/addItem")
  public ResponseEntity<Void> addItemsToCart(
      @PathVariable("shoppingCartId") Integer shoppingCartId,
      @RequestBody AddItemsToCartRequest request) {

    log.info("Adding items to cart {}: product={}, quantity={}",
        shoppingCartId, request.getProductId(), request.getQuantity());

    shoppingCartService.addItemsToCart(shoppingCartId, request);

    return ResponseEntity.noContent().build();
  }

  @Override
  @PostMapping("/shopping-carts/{shoppingCartId}/checkout")
  public ResponseEntity<CheckoutCart200Response> checkoutCart(
      @PathVariable("shoppingCartId") Integer shoppingCartId,
      @RequestBody CheckoutCartRequest request) {

    log.info("Checking out cart {}", shoppingCartId);

    Integer orderId = shoppingCartService.checkoutCart(
        shoppingCartId,
        request.getCreditCardNumber()
    );

    CheckoutCart200Response response = new CheckoutCart200Response();
    response.setOrderId(orderId);

    return ResponseEntity.ok(response);
  }

  /**
   * Exception handlers - use fully qualified name for Error model
   */

  @ExceptionHandler(ShoppingCartService.CartNotFoundException.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handleCartNotFound(
      ShoppingCartService.CartNotFoundException ex) {

    log.error("Cart not found: {}", ex.getMessage());

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("CART_NOT_FOUND");
    error.setMessage(ex.getMessage());

    return ResponseEntity.status(404).body(error);
  }

  @ExceptionHandler(ShoppingCartService.ProductNotFoundException.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handleProductNotFound(
      ShoppingCartService.ProductNotFoundException ex) {

    log.error("Product not found: {}", ex.getMessage());

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("PRODUCT_NOT_FOUND");
    error.setMessage(ex.getMessage());

    return ResponseEntity.status(404).body(error);
  }

  @ExceptionHandler(ShoppingCartService.InvalidCartStateException.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handleInvalidCartState(
      ShoppingCartService.InvalidCartStateException ex) {

    log.error("Invalid cart state: {}", ex.getMessage());

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("INVALID_CART_STATE");
    error.setMessage(ex.getMessage());

    return ResponseEntity.status(400).body(error);
  }

  @ExceptionHandler(ShoppingCartService.PaymentDeclinedException.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handlePaymentDeclined(
      ShoppingCartService.PaymentDeclinedException ex) {

    log.error("Payment declined: {}", ex.getMessage());

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("PAYMENT_DECLINED");
    error.setMessage(ex.getMessage());

    return ResponseEntity.status(402).body(error);
  }

  @ExceptionHandler(ShoppingCartService.ServiceCommunicationException.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handleServiceCommunication(
      ShoppingCartService.ServiceCommunicationException ex) {

    log.error("Service communication error: {}", ex.getMessage());

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("SERVICE_COMMUNICATION_ERROR");
    error.setMessage(ex.getMessage());
    error.setDetails("Could not communicate with external service");

    return ResponseEntity.status(500).body(error);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<com.cs6650.shoppingcart.model.Error> handleGenericException(Exception ex) {
    log.error("Unexpected error: {}", ex.getMessage(), ex);

    com.cs6650.shoppingcart.model.Error error = new com.cs6650.shoppingcart.model.Error();
    error.setError("INTERNAL_SERVER_ERROR");
    error.setMessage("An unexpected error occurred");

    return ResponseEntity.status(500).body(error);
  }
}