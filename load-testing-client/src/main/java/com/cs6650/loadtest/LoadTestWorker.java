package com.cs6650.loadtest;

import com.cs6650.loadtest.model.RequestMetrics;
import com.cs6650.loadtest.util.CreditCardGenerator;
import com.cs6650.loadtest.util.HttpClientService;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Worker thread that processes checkout requests.
 * Each worker creates cart, adds items, and performs checkout.
 */
public class LoadTestWorker implements Runnable {

  private final HttpClientService httpClient;
  private final BlockingQueue<Integer> productIdQueue;
  private final List<RequestMetrics> metrics;
  private final int checkoutsPerWorker;
  private final int itemsPerCart;
  private final AtomicInteger successCounter;
  private final AtomicInteger failureCounter;
  private final CountDownLatch completionLatch;

  /**
   * Constructor for load test worker.
   *
   * @param httpClient HTTP client service for API calls
   * @param productIdQueue Queue of available product IDs
   * @param metrics Thread-safe list to collect metrics
   * @param checkoutsPerWorker Number of checkouts this worker should perform
   * @param itemsPerCart Number of items to add to each cart
   * @param successCounter Atomic counter for successful checkouts
   * @param failureCounter Atomic counter for failed checkouts
   * @param completionLatch Latch to signal worker completion
   */
  public LoadTestWorker(
      HttpClientService httpClient,
      BlockingQueue<Integer> productIdQueue,
      List<RequestMetrics> metrics,
      int checkoutsPerWorker,
      int itemsPerCart,
      AtomicInteger successCounter,
      AtomicInteger failureCounter,
      CountDownLatch completionLatch) {

    this.httpClient = httpClient;
    this.productIdQueue = productIdQueue;
    this.metrics = metrics;
    this.checkoutsPerWorker = checkoutsPerWorker;
    this.itemsPerCart = itemsPerCart;
    this.successCounter = successCounter;
    this.failureCounter = failureCounter;
    this.completionLatch = completionLatch;
  }

  @Override
  public void run() {
    try {
      // Perform assigned number of checkouts
      for (int i = 0; i < checkoutsPerWorker; i++) {
        performCheckout();
      }
    } catch (Exception e) {
      System.err.println("Worker thread error: " + e.getMessage());
      e.printStackTrace();
    } finally {
      // Signal completion
      completionLatch.countDown();
    }
  }

  /**
   * Perform complete checkout flow:
   * 1. Create shopping cart
   * 2. Add items to cart
   * 3. Checkout cart with credit card
   */
  private void performCheckout() {
    try {
      // Generate random customer ID (1 to 100,000)
      int customerId = (int) (Math.random() * 100000) + 1;

      // Step 1: Create shopping cart
      long startTime = System.currentTimeMillis();
      Integer cartId = httpClient.createShoppingCart(customerId);
      long endTime = System.currentTimeMillis();

      // Record create cart metrics
      synchronized (metrics) {
        metrics.add(RequestMetrics.create(
            startTime, endTime, "POST_CREATE_CART", 201));
      }

      // Step 2: Add items to cart
      for (int i = 0; i < itemsPerCart; i++) {
        // Get product ID from queue (blocks if queue is empty)
        Integer productId = productIdQueue.take();

        // Random quantity between 1 and 5
        int quantity = (int) (Math.random() * 5) + 1;

        startTime = System.currentTimeMillis();
        httpClient.addItemToCart(cartId, productId, quantity);
        endTime = System.currentTimeMillis();

        // Record add item metrics
        synchronized (metrics) {
          metrics.add(RequestMetrics.create(
              startTime, endTime, "POST_ADD_ITEM", 204));
        }

        // Return product ID to queue for reuse
        productIdQueue.put(productId);
      }

      // Step 3: Checkout cart
      String creditCard = CreditCardGenerator.generateFakeCreditCard();

      startTime = System.currentTimeMillis();
      int checkoutStatus = httpClient.checkoutCart(cartId, creditCard);
      endTime = System.currentTimeMillis();

      // Record checkout metrics
      synchronized (metrics) {
        metrics.add(RequestMetrics.create(
            startTime, endTime, "POST_CHECKOUT", checkoutStatus));
      }

      // Track success/failure
      if (checkoutStatus == 200 || checkoutStatus == 402) {
        // Both 200 (approved) and 402 (declined) are expected
        successCounter.incrementAndGet();
      } else {
        failureCounter.incrementAndGet();
      }

    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      System.err.println("Worker interrupted: " + e.getMessage());
      failureCounter.incrementAndGet();
    } catch (IOException e) {
      System.err.println("=== IO Exception During Checkout ===");
      System.err.println("Message: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Cause: " + e.getCause().getMessage());
      }
      System.err.println("Stack trace:");
      e.printStackTrace();
      System.err.println("====================================");
      failureCounter.incrementAndGet();
    } catch (Exception e) {
      System.err.println("=== Unexpected Exception ===");
      System.err.println("Type: " + e.getClass().getName());
      System.err.println("Message: " + e.getMessage());
      System.err.println("Stack trace:");
      e.printStackTrace();
      System.err.println("===========================");
      failureCounter.incrementAndGet();
    }
  }
}