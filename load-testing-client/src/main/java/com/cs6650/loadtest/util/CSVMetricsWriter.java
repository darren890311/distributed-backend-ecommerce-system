package com.cs6650.loadtest.util;

import com.cs6650.loadtest.model.RequestMetrics;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes request metrics to CSV file for analysis.
 * Thread-safe when used with synchronized collection.
 */
public class CSVMetricsWriter {

  private static final String[] HEADERS = {
      "start_time", "request_type", "latency_ms", "response_code"
  };

  /**
   * Write metrics to CSV file.
   */
  public static void writeMetrics(List<RequestMetrics> metrics,
      String filename) throws IOException {

    try (FileWriter writer = new FileWriter(filename);
        CSVPrinter csvPrinter = new CSVPrinter(writer,
            CSVFormat.DEFAULT.withHeader(HEADERS))) {

      for (RequestMetrics metric : metrics) {
        csvPrinter.printRecord(
            metric.getStartTime(),
            metric.getRequestType(),
            metric.getLatency(),
            metric.getResponseCode()
        );
      }

      csvPrinter.flush();
    }
  }
}