package com.cs6650.shoppingcartservice.service;

import com.cs6650.shoppingcartservice.entity.CartItemEntity;
import com.cs6650.shoppingcartservice.entity.ShoppingCartEntity;
import com.cs6650.shoppingcart.model.AddItemsToCartRequest;
import com.cs6650.shoppingcart.model.ProcessPaymentRequest;
import com.cs6650.shoppingcart.model.Product;
import com.cs6650.shoppingcartservice.repository.CartItemRepository;
import com.cs6650.shoppingcartservice.repository.ShoppingCartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import com.cs6650.shoppingcartservice.config.RabbitMQConfig;
import com.cs6650.shoppingcartservice.dto.OrderMessage;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Optional;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Business logic for Shopping Cart operations
 * Handles cart creation, adding items, and checkout with payment processing
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShoppingCartService {

  private final ShoppingCartRepository cartRepository;
  private final CartItemRepository cartItemRepository;
  private final RestTemplate restTemplate;
  private final RabbitTemplate rabbitTemplate;

  @Value("${services.product.url}")
  private String productServiceUrl;

  @Value("${services.credit-card-authorizer.url}")
  private String ccaServiceUrl;

  /**
   * Create a new shopping cart
   */
  @Transactional
  public ShoppingCartEntity createCart(Integer customerId) {
    log.info("Creating cart for customer: {}", customerId);

    ShoppingCartEntity cart = new ShoppingCartEntity();
    cart.setCustomerId(customerId);
    cart.setStatus(ShoppingCartEntity.CartStatus.ACTIVE);

    ShoppingCartEntity saved = cartRepository.save(cart);
    log.info("Cart created with ID: {}", saved.getShoppingCartId());

    return saved;
  }

  /**
   * Get cart by ID
   */
  public Optional<ShoppingCartEntity> getCart(Integer cartId) {
    return cartRepository.findById(cartId);
  }

  /**
   * Add items to cart
   * Validates products exist by calling Product Service
   */
  @Transactional
  public void addItemsToCart(Integer cartId, AddItemsToCartRequest request) {
    log.info("Adding items to cart: {}", cartId);

    // Get cart
    ShoppingCartEntity cart = cartRepository.findById(cartId)
        .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartId));

    // Validate cart is active
    if (cart.getStatus() != ShoppingCartEntity.CartStatus.ACTIVE) {
      throw new InvalidCartStateException("Cart is not active: " + cart.getStatus());
    }

    // Validate product exists by calling Product Service
    Integer productId = request.getProductId();
    if (!validateProductExists(productId)) {
      throw new ProductNotFoundException("Product not found: " + productId);
    }

    // Check if product already in cart
    Optional<CartItemEntity> existingItem = cartItemRepository
        .findByShoppingCart_ShoppingCartIdAndProductId(cartId, productId);

    if (existingItem.isPresent()) {
      // Update quantity
      CartItemEntity item = existingItem.get();
      item.setQuantity(item.getQuantity() + request.getQuantity());
      cartItemRepository.save(item);
      log.info("Updated quantity for product {} in cart {}", productId, cartId);
    } else {
      // Add new item
      CartItemEntity newItem = new CartItemEntity(cart, productId, request.getQuantity());
      cartItemRepository.save(newItem);
      log.info("Added product {} to cart {}", productId, cartId);
    }
  }

  /**
   * Checkout cart
   * - Validates cart exists and is active
   * - Calls Credit Card Authorizer to process payment
   * - Publishes order to RabbitMQ for warehouse
   * - Marks cart as checked out
   * - Returns order ID
   */
  @Transactional
  public Integer checkoutCart(Integer cartId, String creditCardNumber) {
    log.info("Checking out cart: {}", cartId);

    // Get cart
    ShoppingCartEntity cart = cartRepository.findById(cartId)
        .orElseThrow(() -> new CartNotFoundException("Cart not found: " + cartId));

    // Validate cart is active
    if (cart.getStatus() != ShoppingCartEntity.CartStatus.ACTIVE) {
      throw new InvalidCartStateException("Cart is not active: " + cart.getStatus());
    }

    // Validate cart has items
    if (cart.getItems().isEmpty()) {
      throw new InvalidCartStateException("Cart is empty");
    }

    // Process payment via Credit Card Authorizer
    boolean paymentAuthorized = authorizePayment(creditCardNumber);

    if (!paymentAuthorized) {
      log.warn("Payment declined for cart: {}", cartId);
      throw new PaymentDeclinedException("Payment was declined");
    }

    // Publish to RabbitMQ
    try {
      publishOrderToWarehouse(cart);
      log.info("Order published to warehouse queue for cart: {}", cartId);
    } catch (Exception e) {
      log.error("Failed to publish order to warehouse: {}", e.getMessage());
      // You could decide whether to fail the checkout or just log the error
      // For now, we'll continue and mark cart as checked out
    }

    // Mark cart as checked out
    cart.setStatus(ShoppingCartEntity.CartStatus.CHECKED_OUT);
    cartRepository.save(cart);

    log.info("Cart {} checked out successfully", cartId);

    // Return cart ID as order ID
    return cartId;
  }

  /**
   * Publish order to RabbitMQ for warehouse processing
   */
  private void publishOrderToWarehouse(ShoppingCartEntity cart) {
    // Convert cart items to order message format
    List<OrderMessage.ProductItem> products = cart.getItems().stream()
        .map(item -> new OrderMessage.ProductItem(
            item.getProductId(),
            item.getQuantity()
        ))
        .collect(Collectors.toList());

    OrderMessage orderMessage = new OrderMessage(
        cart.getShoppingCartId(),
        products
    );

    // Publish to RabbitMQ
    rabbitTemplate.convertAndSend(RabbitMQConfig.CHECKOUT_QUEUE, orderMessage);

    log.info("Published order {} to queue with {} products",
        cart.getShoppingCartId(), products.size());
  }

  /**
   * Validate product exists by calling Product Service
   */
  private boolean validateProductExists(Integer productId) {
    try {
      String url = productServiceUrl + "/products/" + productId;
      log.info("Validating product exists: GET {}", url);

      ResponseEntity<Product> response = restTemplate.getForEntity(url, Product.class);

      boolean exists = response.getStatusCode().is2xxSuccessful();
      log.info("Product {} exists: {}", productId, exists);

      return exists;

    } catch (HttpClientErrorException.NotFound e) {
      log.warn("Product not found: {}", productId);
      return false;
    } catch (Exception e) {
      log.error("Error validating product {}: {}", productId, e.getMessage());
      throw new ServiceCommunicationException("Could not validate product", e);
    }
  }

  /**
   * Authorize payment by calling Credit Card Authorizer
   */
  private boolean authorizePayment(String creditCardNumber) {
    try {
      String url = ccaServiceUrl + "/credit-card-authorizer/authorize";
      log.info("Authorizing payment: POST {}", url);

      ProcessPaymentRequest request = new ProcessPaymentRequest();
      request.setCreditCardNumber(creditCardNumber);

      ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

      // 200 = Authorized, 402 = Declined
      boolean authorized = response.getStatusCode().is2xxSuccessful();
      log.info("Payment authorized: {}", authorized);

      return authorized;

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

  /**
   * Custom exceptions
   */
  public static class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(String message) {
      super(message);
    }
  }

  public static class InvalidCartStateException extends RuntimeException {
    public InvalidCartStateException(String message) {
      super(message);
    }
  }

  public static class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String message) {
      super(message);
    }
  }

  public static class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) {
      super(message);
    }
  }

  public static class ServiceCommunicationException extends RuntimeException {
    public ServiceCommunicationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}