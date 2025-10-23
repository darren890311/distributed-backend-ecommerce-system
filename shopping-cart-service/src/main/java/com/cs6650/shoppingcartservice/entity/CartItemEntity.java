package com.cs6650.shoppingcartservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA Entity for Cart Items
 * Represents products added to a shopping cart
 */
@Entity
@Table(name = "cart_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Integer id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "shopping_cart_id", nullable = false)
  private ShoppingCartEntity shoppingCart;

  @Column(name = "product_id", nullable = false)
  private Integer productId;

  @Column(name = "quantity", nullable = false)
  private Integer quantity;

  /**
   * Constructor without ID (for creating new items)
   */
  public CartItemEntity(ShoppingCartEntity shoppingCart, Integer productId, Integer quantity) {
    this.shoppingCart = shoppingCart;
    this.productId = productId;
    this.quantity = quantity;
  }
}