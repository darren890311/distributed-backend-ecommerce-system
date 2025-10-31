package com.cs6650.loadtest.config;

import lombok.Getter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration loader for load testing parameters.
 * Supports multiple environments: local, aws, or default.
 *
 * Usage:
 *   new LoadTestConfig()           - loads config.properties (default)
 *   new LoadTestConfig("local")    - loads config-local.properties
 *   new LoadTestConfig("aws")      - loads config-aws.properties
 */
@Getter
public class LoadTestConfig {

  // Service URLs
  private final String productServiceUrl;
  private final String shoppingCartServiceUrl;
  private final String creditCardServiceUrl;
  private final String warehouseServiceUrl;

  // Load test parameters
  private final int totalCheckouts;
  private final int itemsPerCart;
  private final int threadCount;
  private final int maxRetries;
  private final int connectionTimeout;
  private final int requestTimeout;
  private final String metricsOutputFile;
  private final String resultsOutputFile;
  private final String rabbitmqManagementUrl;

  // Environment name for display
  private final String environment;

  /**
   * Load default configuration (config.properties).
   */
  public LoadTestConfig() throws IOException {
    this("default");
  }

  /**
   * Load configuration for specific environment.
   *
   * @param environment Environment name: "local", "aws", or "default"
   */
  public LoadTestConfig(String environment) throws IOException {
    this.environment = environment;

    // Determine config file name
    String configFileName;
    if (environment.equals("default")) {
      configFileName = "config.properties";
    } else {
      configFileName = "config-" + environment + ".properties";
    }

    // Load properties
    Properties props = new Properties();

    try (InputStream input = getClass().getClassLoader()
        .getResourceAsStream(configFileName)) {

      if (input == null) {
        throw new IOException(String.format(
            "Unable to find %s. Available profiles: default, local, aws",
            configFileName));
      }

      props.load(input);
    }

    // Load service URLs
    this.productServiceUrl = props.getProperty(
        "product.service.url", "http://localhost:8082");
    this.shoppingCartServiceUrl = props.getProperty(
        "shopping.cart.service.url", "http://localhost:8084");
    this.creditCardServiceUrl = props.getProperty(
        "credit.card.service.url", "http://localhost:8080");
    this.warehouseServiceUrl = props.getProperty(
        "warehouse.service.url", "http://localhost:8083");

    // Load test parameters
    this.totalCheckouts = Integer.parseInt(
        props.getProperty("total.checkouts", "200000"));
    this.itemsPerCart = Integer.parseInt(
        props.getProperty("items.per.cart", "5"));
    this.threadCount = Integer.parseInt(
        props.getProperty("thread.count", "32"));
    this.maxRetries = Integer.parseInt(
        props.getProperty("max.retries", "5"));
    this.connectionTimeout = Integer.parseInt(
        props.getProperty("connection.timeout", "10000"));
    this.requestTimeout = Integer.parseInt(
        props.getProperty("request.timeout", "30000"));
    this.metricsOutputFile = props.getProperty(
        "metrics.output.file", "load_test_metrics.csv");
    this.resultsOutputFile = props.getProperty(
        "results.output.file", "load_test_results.txt");
    this.rabbitmqManagementUrl = props.getProperty(
        "rabbitmq.management.url", "http://localhost:15672");
  }

  /**
   * Print configuration for verification.
   */
  public void printConfig() {
    System.out.println("=== Load Test Configuration ===");
    System.out.println("Environment: " + environment.toUpperCase());
    System.out.println();
    System.out.println("Service URLs:");
    System.out.println("  Product Service:       " + productServiceUrl);
    System.out.println("  Shopping Cart Service: " + shoppingCartServiceUrl);
    System.out.println("  Credit Card Service:   " + creditCardServiceUrl);
    System.out.println("  Warehouse Service:     " + warehouseServiceUrl);
    System.out.println();
    System.out.println("Load Test Parameters:");
    System.out.println("  Total Checkouts: " + totalCheckouts);
    System.out.println("  Items Per Cart:  " + itemsPerCart);
    System.out.println("  Thread Count:    " + threadCount);
    System.out.println("  Max Retries:     " + maxRetries);
    System.out.println();
    System.out.println("Timeouts:");
    System.out.println("  Connection Timeout: " + connectionTimeout + "ms");
    System.out.println("  Request Timeout:    " + requestTimeout + "ms");
    System.out.println();
    System.out.println("Output Files:");
    System.out.println("  Metrics CSV: " + metricsOutputFile);
    System.out.println("  Results TXT: " + resultsOutputFile);
    System.out.println();
    System.out.println("RabbitMQ Management: " + rabbitmqManagementUrl);
    System.out.println("===============================\n");
  }
}