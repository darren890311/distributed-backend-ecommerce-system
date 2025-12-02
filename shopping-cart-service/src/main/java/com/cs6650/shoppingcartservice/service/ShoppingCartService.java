package com.cs6650.shoppingcartservice.service;

import com.cs6650.shoppingcartservice.kvclient.KvStoreClient;
import com.cs6650.shoppingcartservice.client.WarehouseServiceClient;
import com.cs6650.shoppingcartservice.model.ShoppingCart;
import com.cs6650.shoppingcartservice.model.ShoppingCart.CartItem;
import com.cs6650.shoppingcart.model.AddItemsToCartRequest;
import com.cs6650.shoppingcart.model.ProcessPaymentRequest;
import com.cs6650.shoppingcart.model.Product;
import com.cs6650.shoppingcartservice.dto.OrderMessage;
import com.cs6650.shoppingcartservice.config.RabbitMQConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Random;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ShoppingCartService {

  private final KvStoreClient kvStoreClient;
  private final WarehouseServiceClient warehouseClient;
  private final RestTemplate restTemplate;
  private final RabbitTemplate rabbitTemplate;

  @Value("${services.product.url}")
  private String productServiceUrl;

  @Value("${services.credit-card-authorizer.url}")
  private String ccaServiceUrl;

  private final Random random = new Random();

  public ShoppingCart createCart(Integer customerId) throws Exception {
    log.info("Creating cart for customer: {}", customerId);

    Integer cartId = random.nextInt(Integer.MAX_VALUE) + 1;

    ShoppingCart newCart = new ShoppingCart();
    newCart.setShoppingCartId(cartId);
    newCart.setCustomerId(customerId);
    newCart.setStatus("ACTIVE");
    newCart.setItems(new ArrayList<>());

    kvStoreClient.setShoppingCart(cartId, newCart);
    log.info("Cart created with ID: {}", cartId);

    return newCart;
  }
  public void addItemsToCart(Integer cartId, AddItemsToCartRequest request) {
    ShoppingCart cart;
    try {
      cart = kvStoreClient.getShoppingCart(cartId)
          .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartId));
    } catch (Exception e) {
      throw new ServiceCommunicationException("Failed to retrieve cart from DB", e);
    }

    if (!"ACTIVE".equals(cart.getStatus())) {
      throw new InvalidCartStateException("Cart is not active: " + cart.getStatus());
    }

    try {
      kvStoreClient.beginTransaction(cartId);

      Integer productId = request.getProductId();
      Integer quantity = request.getQuantity();
      if (!validateProductExists(productId)) {
        kvStoreClient.abortTransaction(cartId);
        throw new ProductNotFoundException("Product not found: " + productId);
      }
      warehouseClient.checkInventoryAndReserve(productId, quantity);

      Optional<CartItem> existingItem = cart.getItems().stream()
          .filter(item -> item.getProductId().equals(productId))
          .findFirst();

      if (existingItem.isPresent()) {
        CartItem item = existingItem.get();
        item.setQuantity(item.getQuantity() + quantity);
      } else {
        cart.getItems().add(new CartItem(productId, quantity));
      }
      kvStoreClient.setShoppingCart(cartId, cart);

      kvStoreClient.endTransaction(cartId);

    } catch (InsufficientStockException | ProductNotFoundException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during addItemsToCart for Cart {}: {}", cartId, e.getMessage());
      try {
        kvStoreClient.abortTransaction(cartId);
      } catch (Exception abortEx) {
        log.error("Failed to call abort during recovery.", abortEx);
      }
      throw new ServiceCommunicationException("Add item failed due to error: " + e.getMessage(), e);
    }
  }
  public Integer checkoutCart(Integer cartId, String creditCardNumber) throws Exception {
    log.info("Checking out cart: {}", cartId);

    ShoppingCart cart;
    try {
      cart = kvStoreClient.getShoppingCart(cartId)
          .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartId));
    } catch (Exception e) {
      throw new ServiceCommunicationException("Failed to retrieve cart from DB", e);
    }

    if (cart.getStatus().equals("CHECKED_OUT")) {
      throw new InvalidCartStateException("Cart is already checked out.");
    }
    if (cart.getItems().isEmpty()) {
      throw new InvalidCartStateException("Cart is empty");
    }

    try {
      kvStoreClient.beginTransaction(cartId);
      boolean paymentAuthorized = authorizePayment(creditCardNumber);

      if (!paymentAuthorized) {
        kvStoreClient.abortTransaction(cartId);
        throw new PaymentDeclinedException("Payment was declined");
      }
      publishOrderToWarehouse(cart);
      log.info("Order published to warehouse queue for cart: {}", cartId);

      cart.setStatus("CHECKED_OUT");
      cart.setItems(new ArrayList<>());
      kvStoreClient.setShoppingCart(cartId, cart);

      kvStoreClient.endTransaction(cartId);

      return cartId;

    } catch (PaymentDeclinedException e) {
      throw e;
    } catch (Exception e) {
      log.error("Unexpected error during checkout for Cart {}: {}", cartId, e.getMessage());
      try {
        kvStoreClient.abortTransaction(cartId);
      } catch (Exception abortEx) {
        log.error("Failed to call abort during recovery.", abortEx);
      }
      throw new ServiceCommunicationException("Checkout failed due to error: " + e.getMessage(), e);
    }
  }

  private void publishOrderToWarehouse(ShoppingCart cart) {
    List<OrderMessage.ProductItem> products = cart.getItems().stream()
        .map(item -> new OrderMessage.ProductItem(item.getProductId(), item.getQuantity()))
        .collect(Collectors.toList());

    OrderMessage orderMessage = new OrderMessage(cart.getShoppingCartId(), products);
    rabbitTemplate.convertAndSend(RabbitMQConfig.CHECKOUT_QUEUE, orderMessage);

    log.info("Published order {} to queue with {} products", cart.getShoppingCartId(), products.size());
  }

  private boolean validateProductExists(Integer productId) {
    try {
      String url = productServiceUrl + "/products/" + productId;
      log.info("Validating product exists: GET {}", url);

      ResponseEntity<Product> response = restTemplate.getForEntity(url, Product.class);

      return response.getStatusCode().is2xxSuccessful();

    } catch (HttpClientErrorException.NotFound e) {
      log.warn("Product not found: {}", productId);
      return false;
    } catch (Exception e) {
      log.error("Error validating product {}: {}", productId, e.getMessage());
      throw new ServiceCommunicationException("Could not validate product", e);
    }
  }

  private boolean authorizePayment(String creditCardNumber) {
    try {
      String url = ccaServiceUrl + "/credit-card-authorizer/authorize";
      log.info("Authorizing payment: POST {}", url);

      ProcessPaymentRequest request = new ProcessPaymentRequest();
      request.setCreditCardNumber(creditCardNumber);

      ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

      return response.getStatusCode().is2xxSuccessful();

    } catch (HttpClientErrorException e) {
      if (e.getStatusCode().value() == 402) {
        log.warn("Payment declined (402)");
        return false;
      }
      log.error("Error authorizing payment: {}", e.getMessage());
      throw new ServiceCommunicationException("Could not authorize payment", e);
    } catch (Exception e) {
      log.error("Error authorizing payment: {}", e.getMessage());
      throw new ServiceCommunicationException("Could not authorize payment", e);
    }
  }

  public static class CartNotFoundException extends RuntimeException { public CartNotFoundException(String message) { super(message); } }
  public static class InvalidCartStateException extends RuntimeException { public InvalidCartStateException(String message) { super(message); } }

  public static class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) { super(message); }
  }

  public static class PaymentDeclinedException extends RuntimeException { public PaymentDeclinedException(String message) { super(message); } }

  public static class ServiceCommunicationException extends RuntimeException {
    public ServiceCommunicationException(String message, Throwable cause) { super(message, cause); }
  }

  public static class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String message) { super(message); }
  }
}