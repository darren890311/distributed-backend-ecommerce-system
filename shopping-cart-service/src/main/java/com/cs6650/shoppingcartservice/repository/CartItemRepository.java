package com.cs6650.shoppingcartservice.repository;

import com.cs6650.shoppingcartservice.entity.CartItemEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository for Cart Items
 */
@Repository
public interface CartItemRepository extends JpaRepository<CartItemEntity, Integer> {

  /**
   * Find all items for a specific cart
   */
  List<CartItemEntity> findByShoppingCart_ShoppingCartId(Integer shoppingCartId);

  /**
   * Find specific product in a cart
   */
  Optional<CartItemEntity> findByShoppingCart_ShoppingCartIdAndProductId(
      Integer shoppingCartId,
      Integer productId
  );
}