package com.cs6650.loadtest.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for storing individual request metrics.
 * Used for calculating mean, median, p99, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestMetrics {

  private long startTime;        // Timestamp when request started (epoch millis)
  private String requestType;    // POST, GET, etc.
  private long latency;          // Response time in milliseconds
  private int responseCode;      // HTTP status code
  private boolean successful;    // Whether request succeeded (200-299)

  /**
   * Calculate latency from start and end times.
   */
  public static RequestMetrics create(long startTime, long endTime,
      String requestType, int responseCode) {
    long latency = endTime - startTime;
    boolean successful = responseCode >= 200 && responseCode < 300;

    return RequestMetrics.builder()
        .startTime(startTime)
        .requestType(requestType)
        .latency(latency)
        .responseCode(responseCode)
        .successful(successful)
        .build();
  }
}