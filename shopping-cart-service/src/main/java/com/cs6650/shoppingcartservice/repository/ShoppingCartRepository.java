package com.cs6650.shoppingcartservice.repository;

import com.cs6650.shoppingcartservice.entity.ShoppingCartEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA Repository for Shopping Cart
 */
@Repository
public interface ShoppingCartRepository extends JpaRepository<ShoppingCartEntity, Integer> {

  /**
   * Find active cart by customer ID
   */
  Optional<ShoppingCartEntity> findByCustomerIdAndStatus(
      Integer customerId,
      ShoppingCartEntity.CartStatus status
  );
}