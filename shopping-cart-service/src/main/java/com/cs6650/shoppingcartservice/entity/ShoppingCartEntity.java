package com.cs6650.shoppingcartservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA Entity for Shopping Cart
 * Stores shopping carts with their items
 */
@Entity
@Table(name = "shopping_carts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShoppingCartEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "shopping_cart_id")
  private Integer shoppingCartId;

  @Column(name = "customer_id", nullable = false)
  private Integer customerId;

  @Column(name = "created_at", nullable = false)
  private LocalDateTime createdAt;

  @Column(name = "status", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private CartStatus status;

  @OneToMany(mappedBy = "shoppingCart", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CartItemEntity> items = new ArrayList<>();

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    if (status == null) {
      status = CartStatus.ACTIVE;
    }
  }

  /**
   * Cart status enum
   */
  public enum CartStatus {
    ACTIVE,       // Cart is open, can add items
    CHECKED_OUT,  // Cart has been checked out
    ABANDONED     // Cart was abandoned
  }
}