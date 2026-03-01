package com.hisaablite.repository;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShop(Shop shop);

    List<Product> findByShopAndActiveTrue(Shop shop);
    
   @Query("""
       SELECT p FROM Product p
       WHERE p.shop = :shop
       AND p.stockQuantity <= p.minStock
       """)
    List<Product> findLowStockProducts(@Param("shop") Shop shop);

    // List<Product> findByShopAndNameContainingIgnoreCase(Shop shop, String name);
    // List<Product> findByShopAndNameStartingWithIgnoreCase(Shop shop, String name);
    List<Product> findByShopAndNameIgnoreCaseContaining(Shop shop, String name);




}

