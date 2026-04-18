package com.expygen.repository;

import com.expygen.entity.Product;
import com.expygen.entity.Shop;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByShop(Shop shop);

    List<Product> findByShopAndActiveTrue(Shop shop);

    List<Product> findByShopAndActiveTrueAndIdIn(Shop shop, List<Long> ids);

    Page<Product> findByShopAndActiveTrue(Shop shop, Pageable pageable);
    
    @Query("""
            SELECT p FROM Product p
            WHERE p.shop = :shop
            AND p.stockQuantity <= p.minStock
            """)
    List<Product> findLowStockProducts(@Param("shop") Shop shop);

    List<Product> findByShopAndNameIgnoreCaseContaining(Shop shop, String name);

    Optional<Product> findByShopAndNameIgnoreCaseAndActiveTrue(Shop shop, String name);

    Optional<Product> findByIdAndShop(Long id, Shop shop);

    boolean existsByShopAndBarcodeIgnoreCaseAndActiveTrue(Shop shop, String barcode);

    boolean existsByShopAndBarcodeIgnoreCaseAndActiveTrueAndIdNot(Shop shop, String barcode, Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Product p WHERE p.id = :id AND p.shop = :shop")
    Optional<Product> findByIdAndShopForUpdate(@Param("id") Long id, @Param("shop") Shop shop);

    @Modifying
    @Query("UPDATE Product p SET p.version = 0 WHERE p.id = :id AND p.version IS NULL")
    int initializeVersionIfMissing(@Param("id") Long id);

    @Query("SELECT COUNT(p) FROM Product p WHERE p.shop = :shop")
    long countByShop(@Param("shop") Shop shop);


    // Enhanced search across medical-friendly fields
    @Query("""
            SELECT p FROM Product p 
            WHERE p.shop = :shop 
            AND p.active = true 
            AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) 
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.genericName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.manufacturer, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.packSize, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CAST(p.price AS string) LIKE CONCAT('%', :keyword, '%')
            )
            ORDER BY CASE
                WHEN LOWER(COALESCE(p.barcode, '')) = LOWER(:keyword) THEN 0
                ELSE 1
            END,
            p.name ASC
            """)
    List<Product> searchProducts(@Param("shop") Shop shop, @Param("keyword") String keyword);

    @Query(value = """
            SELECT p FROM Product p
            WHERE p.shop = :shop
            AND p.active = true
            AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.genericName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.manufacturer, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.packSize, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CAST(p.price AS string) LIKE CONCAT('%', :keyword, '%')
            )
            ORDER BY CASE
                WHEN LOWER(COALESCE(p.barcode, '')) = LOWER(:keyword) THEN 0
                ELSE 1
            END,
            p.name ASC
            """,
            countQuery = """
            SELECT COUNT(p) FROM Product p
            WHERE p.shop = :shop
            AND p.active = true
            AND (
                LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.genericName, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.manufacturer, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.packSize, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR CAST(p.price AS string) LIKE CONCAT('%', :keyword, '%')
            )
            """)
    Page<Product> searchProductsWithPagination(@Param("shop") Shop shop,
                                               @Param("keyword") String keyword,
                                               Pageable pageable);

    long countByShopAndActiveTrue(Shop shop);
}
