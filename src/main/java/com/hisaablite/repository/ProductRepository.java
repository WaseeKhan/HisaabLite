package com.hisaablite.repository;

import com.hisaablite.entity.Product;
import com.hisaablite.entity.Shop;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShop(Shop shop);

    List<Product> findByShopAndActiveTrue(Shop shop);
  Page<Product> findByShopAndActiveTrue(Shop shop, Pageable pageable);
    
   Page<Product> findByShopAndNameContainingIgnoreCaseAndActiveTrue(
        Shop shop, 
        String name, 
        Pageable pageable
    );
    
    @Query("""
            SELECT p FROM Product p
            WHERE p.shop = :shop
            AND p.stockQuantity <= p.minStock
            """)
    List<Product> findLowStockProducts(@Param("shop") Shop shop);

    List<Product> findByShopAndNameIgnoreCaseContaining(Shop shop, String name);

    Optional<Product> findByIdAndShop(Long id, Shop shop);

      @Query("SELECT COUNT(p) FROM Product p WHERE p.shop = :shop")
    long countByShop(@Param("shop") Shop shop);


     // Enhanced search across multiple fields
    @Query("""
            SELECT p FROM Product p 
            WHERE p.shop = :shop 
            AND p.active = true 
            AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
                OR LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CAST(p.price AS string) LIKE CONCAT('%', :keyword, '%')
            )
            """)
    List<Product> searchProducts(@Param("shop") Shop shop, @Param("keyword") String keyword);


}
