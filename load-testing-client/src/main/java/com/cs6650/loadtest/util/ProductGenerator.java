package com.cs6650.loadtest.util;

import com.cs6650.loadtest.model.Product;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generates random product data for testing.
 * Thread-safe using ThreadLocalRandom.
 */
public class ProductGenerator {

  private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final String[] MANUFACTURERS = {
      "TechCorp", "GlobalMfg", "InnovateCo", "PrecisionParts", "QualityGoods",
      "MegaBrand", "EliteProducts", "PrimeMaker", "SuperiorMfg", "UltraGoods"
  };

  /**
   * Generate a random product following API specifications.
   * Includes a dummy product_id to satisfy validation (server will regenerate it).
   */
  public static Product generateRandomProduct() {
    ThreadLocalRandom random = ThreadLocalRandom.current();

    return Product.builder()
        .productId(random.nextInt(1, 1000000))  // Add dummy ID for validation
        .sku(generateSKU())
        .manufacturer(getRandomManufacturer())
        .categoryId(random.nextInt(1, 100_001))  // 1 to 100,000
        .weight(random.nextInt(100, 10_001))     // 100g to 10kg
        .someOtherId(random.nextInt(1, Integer.MAX_VALUE))
        .build();
  }

  /**
   * Generate SKU: 10 random characters from {A-Z0-9}.
   */
  private static String generateSKU() {
    StringBuilder sku = new StringBuilder(10);
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < 10; i++) {
      sku.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }

    return sku.toString();
  }

  /**
   * Get a random manufacturer name.
   */
  private static String getRandomManufacturer() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    return MANUFACTURERS[random.nextInt(MANUFACTURERS.length)];
  }
}