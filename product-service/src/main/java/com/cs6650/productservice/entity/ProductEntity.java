package com.cs6650.productservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for Product
 * Matches the OpenAPI Product schema
 */
@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer productId;

  @Column(nullable = false, length = 100)
  private String sku;

  @Column(nullable = false, length = 200)
  private String manufacturer;

  @Column(nullable = false)
  private Integer categoryId;

  @Column(nullable = false)
  private Integer weight;

  @Column(nullable = false)
  private Integer someOtherId;
}