package com.cs6650.loadtest.util;

import com.cs6650.loadtest.model.RequestMetrics;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Calculates statistics from request metrics.
 * Computes mean, median, p99, min, max, and throughput.
 */
@Data
public class MetricsCalculator {

  private final long totalRequests;
  private final long successfulRequests;
  private final long failedRequests;
  private final double meanLatency;
  private final double medianLatency;
  private final double p99Latency;
  private final long minLatency;
  private final long maxLatency;
  private final double throughput;  // requests per second
  private final long wallTime;      // total time in milliseconds

  /**
   * Calculate all metrics from the list of request metrics.
   */
  public static MetricsCalculator calculate(List<RequestMetrics> metrics,
      long wallTimeMs) {

    long totalRequests = metrics.size();
    long successfulRequests = metrics.stream()
        .filter(RequestMetrics::isSuccessful)
        .count();
    long failedRequests = totalRequests - successfulRequests;

    // Sort latencies for percentile calculations
    List<Long> sortedLatencies = metrics.stream()
        .map(RequestMetrics::getLatency)
        .sorted()
        .collect(Collectors.toList());

    // Calculate statistics
    double meanLatency = sortedLatencies.stream()
        .mapToLong(Long::longValue)
        .average()
        .orElse(0.0);

    double medianLatency = calculatePercentile(sortedLatencies, 50);
    double p99Latency = calculatePercentile(sortedLatencies, 99);

    long minLatency = sortedLatencies.isEmpty() ? 0 : sortedLatencies.get(0);
    long maxLatency = sortedLatencies.isEmpty() ? 0 :
        sortedLatencies.get(sortedLatencies.size() - 1);

    // Throughput = total requests / (wall time in seconds)
    double throughput = (totalRequests * 1000.0) / wallTimeMs;

    return new MetricsCalculator(
        totalRequests,
        successfulRequests,
        failedRequests,
        meanLatency,
        medianLatency,
        p99Latency,
        minLatency,
        maxLatency,
        throughput,
        wallTimeMs
    );
  }

  /**
   * Calculate percentile value from sorted list.
   */
  private static double calculatePercentile(List<Long> sortedValues, int percentile) {
    if (sortedValues.isEmpty()) {
      return 0.0;
    }

    int index = (int) Math.ceil((percentile / 100.0) * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));

    return sortedValues.get(index);
  }

  /**
   * Print formatted results to console.
   */
  public void printResults() {
    System.out.println("\n=== Load Test Results ===");
    System.out.println("Total Requests: " + totalRequests);
    System.out.println("Successful Requests: " + successfulRequests);
    System.out.println("Failed Requests: " + failedRequests);
    System.out.printf("Success Rate: %.2f%%\n",
        (successfulRequests * 100.0 / totalRequests));
    System.out.println();

    System.out.println("=== Latency Statistics ===");
    System.out.printf("Mean Latency: %.2f ms\n", meanLatency);
    System.out.printf("Median Latency: %.2f ms\n", medianLatency);
    System.out.printf("P99 Latency: %.2f ms\n", p99Latency);
    System.out.printf("Min Latency: %d ms\n", minLatency);
    System.out.printf("Max Latency: %d ms\n", maxLatency);
    System.out.println();

    System.out.println("=== Throughput ===");
    System.out.printf("Wall Time: %.2f seconds\n", wallTime / 1000.0);
    System.out.printf("Throughput: %.2f requests/second\n", throughput);
    System.out.println("========================\n");
  }

  /**
   * Write results to file.
   */
  public void writeToFile(String filename) throws java.io.IOException {
    try (java.io.PrintWriter writer = new java.io.PrintWriter(filename)) {
      writer.println("=== Load Test Results ===");
      writer.println("Total Requests: " + totalRequests);
      writer.println("Successful Requests: " + successfulRequests);
      writer.println("Failed Requests: " + failedRequests);
      writer.printf("Success Rate: %.2f%%\n",
          (successfulRequests * 100.0 / totalRequests));
      writer.println();

      writer.println("=== Latency Statistics ===");
      writer.printf("Mean Latency: %.2f ms\n", meanLatency);
      writer.printf("Median Latency: %.2f ms\n", medianLatency);
      writer.printf("P99 Latency: %.2f ms\n", p99Latency);
      writer.printf("Min Latency: %d ms\n", minLatency);
      writer.printf("Max Latency: %d ms\n", maxLatency);
      writer.println();

      writer.println("=== Throughput ===");
      writer.printf("Wall Time: %.2f seconds\n", wallTime / 1000.0);
      writer.printf("Throughput: %.2f requests/second\n", throughput);
      writer.println("========================");
    }
  }
}