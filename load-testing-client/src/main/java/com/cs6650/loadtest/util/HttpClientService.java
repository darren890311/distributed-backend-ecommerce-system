package com.cs6650.loadtest.util;

import com.cs6650.loadtest.config.LoadTestConfig;
import com.cs6650.loadtest.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client service for making API requests.
 * Implements connection pooling and retry logic for 5xx errors.
 * Routes requests to appropriate microservices based on operation.
 */
public class HttpClientService {

  private final CloseableHttpClient httpClient;
  private final ObjectMapper objectMapper;

  // Service URLs - UPDATED to use separate URLs
  private final String productServiceUrl;
  private final String shoppingCartServiceUrl;
  private final String creditCardServiceUrl;
  private final String warehouseServiceUrl;

  private final int maxRetries;

  /**
   * Initialize HTTP client with connection pooling.
   */
  public HttpClientService(LoadTestConfig config) {
    // Store all service URLs from config
    this.productServiceUrl = config.getProductServiceUrl();
    this.shoppingCartServiceUrl = config.getShoppingCartServiceUrl();
    this.creditCardServiceUrl = config.getCreditCardServiceUrl();
    this.warehouseServiceUrl = config.getWarehouseServiceUrl();

    this.maxRetries = config.getMaxRetries();
    this.objectMapper = new ObjectMapper();

    // Configure connection pooling for high concurrency
    PoolingHttpClientConnectionManager connectionManager =
        new PoolingHttpClientConnectionManager();
    connectionManager.setMaxTotal(200);  // Max total connections
    connectionManager.setDefaultMaxPerRoute(50);  // Max per route

    // Configure timeouts
    RequestConfig requestConfig = RequestConfig.custom()
        .setConnectionRequestTimeout(Timeout.of(config.getConnectionTimeout(),
            TimeUnit.MILLISECONDS))
        .setResponseTimeout(Timeout.of(config.getRequestTimeout(),
            TimeUnit.MILLISECONDS))
        .build();

    // Build HTTP client
    this.httpClient = HttpClients.custom()
        .setConnectionManager(connectionManager)
        .setDefaultRequestConfig(requestConfig)
        .build();
  }

  /**
   * Create a new product.
   * Routes to Product Service (port 8082).
   * Returns the server-generated product_id.
   */
  public Integer createProduct(Product product) throws IOException {
    String url = productServiceUrl + "/products";  // ✅ Use Product Service URL
    String jsonBody = objectMapper.writeValueAsString(product);

    HttpPost request = new HttpPost(url);
    request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

    String response = executeWithRetry(request);

    // Parse response to get product_id
    // Response format: {"product_id": 123}
    var jsonNode = objectMapper.readTree(response);
    return jsonNode.get("product_id").asInt();
  }

  /**
   * Create a new shopping cart.
   * Routes to Shopping Cart Service (port 8084).
   * Returns the shopping_cart_id.
   */
  public Integer createShoppingCart(Integer customerId) throws IOException {
    String url = shoppingCartServiceUrl + "/shopping-cart";  // ✅ Use Shopping Cart URL
    String jsonBody = String.format("{\"customer_id\": %d}", customerId);

    HttpPost request = new HttpPost(url);
    request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

    String response = executeWithRetry(request);

    // Parse response
    var jsonNode = objectMapper.readTree(response);
    return jsonNode.get("shopping_cart_id").asInt();
  }

  /**
   * Add item to shopping cart.
   * Routes to Shopping Cart Service (port 8084).
   */
  public void addItemToCart(Integer cartId, Integer productId,
      Integer quantity) throws IOException {
    String url = String.format("%s/shopping-carts/%d/addItem",
        shoppingCartServiceUrl, cartId);  // ✅ Use Shopping Cart URL

    CartItem item = CartItem.builder()
        .productId(productId)
        .quantity(quantity)
        .build();

    String jsonBody = objectMapper.writeValueAsString(item);

    HttpPost request = new HttpPost(url);
    request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

    executeWithRetry(request);
  }

  /**
   * Checkout shopping cart.
   * Routes to Shopping Cart Service (port 8084).
   * Shopping Cart Service internally calls Credit Card Service (port 8080).
   * Returns HTTP status code (200 for approved, 402 for declined).
   */
  public int checkoutCart(Integer cartId, String creditCardNumber) throws IOException {
    String url = String.format("%s/shopping-carts/%d/checkout",
        shoppingCartServiceUrl, cartId);  // ✅ Use Shopping Cart URL

    CheckoutRequest checkout = CheckoutRequest.builder()
        .creditCardNumber(creditCardNumber)
        .build();

    String jsonBody = objectMapper.writeValueAsString(checkout);

    HttpPost request = new HttpPost(url);
    request.setEntity(new StringEntity(jsonBody, ContentType.APPLICATION_JSON));

    // For checkout, we want to return the actual status code
    // Don't retry on 402 (payment declined) as it's expected
    return executeCheckoutRequest(request);
  }

  /**
   * Execute HTTP request with retry logic for 5xx errors.
   */
  private String executeWithRetry(HttpPost request) throws IOException {
    int attempt = 0;
    IOException lastException = null;

    while (attempt <= maxRetries) {
      try (CloseableHttpResponse response = httpClient.execute(request)) {
        int statusCode = response.getCode();

        // Handle 204 No Content - no response body
        if (statusCode == 204) {
          // Consume entity if present (though 204 shouldn't have one)
          if (response.getEntity() != null) {
            EntityUtils.consume(response.getEntity());
          }
          return "";  // Return empty string for no content
        }

        // Get response body for other 2xx responses
        String responseBody = "";
        if (response.getEntity() != null) {
          try {
            responseBody = EntityUtils.toString(response.getEntity());
          } catch (ParseException e) {
            throw new IOException("Failed to parse response body", e);
          }
        }

        // Success (2xx)
        if (statusCode >= 200 && statusCode < 300) {
          return responseBody;
        }

        // Client error (4xx) - don't retry
        if (statusCode >= 400 && statusCode < 500) {
          throw new IOException(String.format(
              "Client error %d: %s", statusCode, responseBody));
        }

        // Server error (5xx) - retry
        if (statusCode >= 500) {
          attempt++;
          if (attempt <= maxRetries) {
            // Exponential backoff
            try {
              Thread.sleep((long) Math.pow(2, attempt) * 100);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw new IOException("Retry interrupted", ie);
            }
            continue;
          }
          throw new IOException(String.format(
              "Server error %d after %d retries: %s",
              statusCode, maxRetries, responseBody));
        }

      } catch (IOException e) {
        lastException = e;
        attempt++;
        if (attempt <= maxRetries) {
          try {
            Thread.sleep((long) Math.pow(2, attempt) * 100);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Retry interrupted", ie);
          }
        }
      }
    }

    throw new IOException("Max retries exceeded", lastException);
  }

  /**
   * Execute checkout request - special handling for 402 (payment declined).
   */
  private int executeCheckoutRequest(HttpPost request) throws IOException {
    int attempt = 0;
    IOException lastException = null;

    while (attempt <= maxRetries) {
      CloseableHttpResponse response = null;
      try {
        response = httpClient.execute(request);
        int statusCode = response.getCode();

        // Read and consume the response body to properly release the connection
        String responseBody = null;
        if (response.getEntity() != null) {
          try {
            responseBody = EntityUtils.toString(response.getEntity());
          } catch (ParseException e) {
            // If we can't parse, at least consume the entity
            try {
              EntityUtils.consume(response.getEntity());
            } catch (Exception consumeEx) {
              // Ignore consume errors
            }
          }
        }

        // Success or expected payment declined
        if (statusCode == 200 || statusCode == 402) {
          return statusCode;
        }

        // Other 4xx errors - don't retry
        if (statusCode >= 400 && statusCode < 500) {
          throw new IOException(String.format(
              "Client error %d: %s", statusCode,
              responseBody != null ? responseBody : "No response body"));
        }

        // Server error (5xx) - retry
        if (statusCode >= 500) {
          attempt++;
          if (attempt <= maxRetries) {
            try {
              Thread.sleep((long) Math.pow(2, attempt) * 100);
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              throw new IOException("Retry interrupted", ie);
            }
            continue;
          }
          throw new IOException(String.format(
              "Server error %d after %d retries", statusCode, maxRetries));
        }

      } catch (IOException e) {
        lastException = e;
        attempt++;
        if (attempt <= maxRetries) {
          try {
            Thread.sleep((long) Math.pow(2, attempt) * 100);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("Retry interrupted", ie);
          }
        }
      } finally {
        // Always close the response
        if (response != null) {
          try {
            response.close();
          } catch (IOException e) {
            // Ignore close errors
          }
        }
      }
    }

    throw new IOException("Max retries exceeded", lastException);
  }

  /**
   * Close HTTP client and release resources.
   */
  public void close() throws IOException {
    if (httpClient != null) {
      httpClient.close();
    }
  }
}