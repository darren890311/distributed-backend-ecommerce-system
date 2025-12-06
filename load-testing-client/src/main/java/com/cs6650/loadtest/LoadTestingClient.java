package com.cs6650.loadtest;

import com.cs6650.loadtest.config.LoadTestConfig;
import com.cs6650.loadtest.model.Product;
import com.cs6650.loadtest.model.RequestMetrics;
import com.cs6650.loadtest.util.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main load testing client
 *
 * Performs multi-threaded load testing of e-commerce microservices.
 * Supports multiple environments: local, aws, or default.
 *
 * Usage:
 *   mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient"
 *   mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="local"
 *   mvn exec:java -Dexec.mainClass="com.cs6650.loadtest.LoadTestingClient" -Dexec.args="aws"
 */
public class LoadTestingClient {

  // Configuration: Pre-load 1,000 products as required by Assignment 5
  // Products are created in Phase 1 before load testing begins
  // All products stored in distributed KV database
  private static final int PRODUCT_POOL_SIZE = 1000;

  public static void main(String[] args) {
    System.out.println("=== CS6650 Assignment 3 - Load Testing Client ===\n");

    try {
      // Determine environment from command line args
      String environment = "default";
      if (args.length > 0) {
        environment = args[0];
        System.out.println("Using environment: " + environment.toUpperCase() + "\n");
      } else {
        System.out.println("No environment specified, using DEFAULT\n");
        System.out.println("Available environments: local, aws");
        System.out.println("Usage: mvn exec:java -Dexec.args=\"local\"\n");
      }

      // Load configuration
      LoadTestConfig config = new LoadTestConfig(environment);
      config.printConfig();

      // Initialize HTTP client
      HttpClientService httpClient = new HttpClientService(config);

      // Phase 1: Pre-create products
      System.out.println("Phase 1: Creating product pool...");
      BlockingQueue<Integer> productIdQueue = createProductPool(httpClient);
      System.out.printf("Created %d products\n\n", productIdQueue.size());

      // Phase 2: Perform load test
      System.out.println("Phase 2: Starting load test...");
      System.out.printf("Target: %d checkouts with %d threads\n\n",
          config.getTotalCheckouts(), config.getThreadCount());

      LoadTestResults results = performLoadTest(
          httpClient,
          productIdQueue,
          config
      );

      // Phase 3: Generate reports
      System.out.println("\nPhase 3: Generating reports...");
      generateReports(results, config);

      // Cleanup
      httpClient.close();

      System.out.println("\n=== Load Test Complete ===");
      System.out.println("Check the following files for detailed results:");
      System.out.println("  - " + config.getMetricsOutputFile());
      System.out.println("  - " + config.getResultsOutputFile());

    } catch (Exception e) {
      System.err.println("Load test failed: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Pre-create a pool of products to use during load testing.
   * Returns a queue of product IDs.
   */
  private static BlockingQueue<Integer> createProductPool(
      HttpClientService httpClient) throws IOException {

    BlockingQueue<Integer> productQueue = new LinkedBlockingQueue<>();
    List<Integer> productIds = new ArrayList<>();

    for (int i = 0; i < PRODUCT_POOL_SIZE; i++) {
      Product product = ProductGenerator.generateRandomProduct();
      Integer productId = httpClient.createProduct(product);
      productQueue.add(productId);
      productIds.add(productId);

      // Progress indicator every 100 products
      if ((i + 1) % 100 == 0) {
        System.out.printf("Created %d/%d products\n", i + 1, PRODUCT_POOL_SIZE);
      }
    }

    // Export product IDs to JSON file for Locust load testing
    // Write to parent directory (cs6650-assignment5/) where locustfile_aws.py is located
    String jsonPath = "../products.json";
    try (java.io.PrintWriter jsonWriter = new java.io.PrintWriter(jsonPath)) {
      StringBuilder json = new StringBuilder();
      json.append("{\n  \"product_ids\": [");
      for (int i = 0; i < productIds.size(); i++) {
        if (i > 0) json.append(", ");
        if (i % 20 == 0) json.append("\n    ");
        json.append(productIds.get(i));
      }
      json.append("\n  ],\n  \"count\": ").append(productIds.size());
      json.append(",\n  \"timestamp\": \"").append(java.time.Instant.now()).append("\"");
      json.append("\n}\n");
      jsonWriter.print(json.toString());
      System.out.println("Product IDs exported to " + jsonPath + " for Locust (JSON format)");
    } catch (Exception e) {
      System.err.println("Warning: Could not write products.json: " + e.getMessage());
    }

    // Also write simple text file as backup
    try (java.io.PrintWriter writer = new java.io.PrintWriter("/tmp/product_ids.txt")) {
      for (Integer id : productIds) {
        writer.println(id);
      }
      System.out.println("Product IDs also exported to /tmp/product_ids.txt");
    } catch (Exception e) {
      System.err.println("Warning: Could not write product IDs to file: " + e.getMessage());
    }

    return productQueue;
  }

  /**
   * Perform the main load test using multiple worker threads.
   */
  private static LoadTestResults performLoadTest(
      HttpClientService httpClient,
      BlockingQueue<Integer> productIdQueue,
      LoadTestConfig config) throws InterruptedException {

    // Metrics collection (thread-safe)
    List<RequestMetrics> metrics = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger successCounter = new AtomicInteger(0);
    AtomicInteger failureCounter = new AtomicInteger(0);

    // Calculate checkouts per worker
    int threadCount = config.getThreadCount();
    int totalCheckouts = config.getTotalCheckouts();
    int checkoutsPerWorker = totalCheckouts / threadCount;

    // Latch to wait for all workers to complete
    CountDownLatch completionLatch = new CountDownLatch(threadCount);

    // Thread pool
    ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

    // Record start time
    long startTime = System.currentTimeMillis();

    // Launch worker threads
    System.out.println("Launching worker threads...");
    for (int i = 0; i < threadCount; i++) {
      LoadTestWorker worker = new LoadTestWorker(
          httpClient,
          productIdQueue,
          metrics,
          checkoutsPerWorker,
          config.getItemsPerCart(),
          successCounter,
          failureCounter,
          completionLatch
      );
      executorService.submit(worker);
    }

    // Progress monitoring thread
    Thread progressThread = new Thread(() -> {
      try {
        while (!completionLatch.await(30, TimeUnit.SECONDS)) {
          int completed = successCounter.get() + failureCounter.get();
          double progress = (completed * 100.0) / totalCheckouts;
          System.out.printf("Progress: %d/%d checkouts (%.1f%%)\n",
              completed, totalCheckouts, progress);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    });
    progressThread.start();

    // Wait for all workers to complete
    completionLatch.await();
    progressThread.interrupt();

    // Record end time
    long endTime = System.currentTimeMillis();
    long wallTime = endTime - startTime;

    // Shutdown executor
    executorService.shutdown();
    executorService.awaitTermination(30, TimeUnit.SECONDS);

    System.out.println("\nAll workers completed!");

    return new LoadTestResults(
        metrics,
        successCounter.get(),
        failureCounter.get(),
        wallTime
    );
  }

  /**
   * Generate and save metrics reports.
   */
  private static void generateReports(
      LoadTestResults results,
      LoadTestConfig config) throws IOException {

    // Calculate statistics
    MetricsCalculator calculator = MetricsCalculator.calculate(
        results.metrics,
        results.wallTime
    );

    // Print to console
    calculator.printResults();

    // Print additional info
    System.out.println("=== Checkout Results ===");
    System.out.println("Successful Checkouts: " + results.successCount);
    System.out.println("Failed Checkouts: " + results.failureCount);
    System.out.println("========================\n");

    // Write CSV metrics
    CSVMetricsWriter.writeMetrics(
        results.metrics,
        config.getMetricsOutputFile()
    );
    System.out.println("CSV metrics written to: " + config.getMetricsOutputFile());

    // Write text results
    calculator.writeToFile(config.getResultsOutputFile());
    System.out.println("Results written to: " + config.getResultsOutputFile());
  }

  /**
   * Container for load test results.
   */
  private static class LoadTestResults {
    final List<RequestMetrics> metrics;
    final int successCount;
    final int failureCount;
    final long wallTime;

    LoadTestResults(List<RequestMetrics> metrics, int successCount,
        int failureCount, long wallTime) {
      this.metrics = metrics;
      this.successCount = successCount;
      this.failureCount = failureCount;
      this.wallTime = wallTime;
    }
  }
}