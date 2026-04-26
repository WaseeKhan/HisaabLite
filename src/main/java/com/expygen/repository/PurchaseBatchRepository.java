package com.expygen.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.Product;
import com.expygen.entity.PurchaseBatch;
import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;

import jakarta.persistence.LockModeType;

public interface PurchaseBatchRepository extends JpaRepository<PurchaseBatch, Long> {

    @EntityGraph(attributePaths = { "product", "purchaseEntry", "purchaseEntry.supplier" })
    Page<PurchaseBatch> findByShopAndActiveTrueOrderByCreatedAtDesc(Shop shop, Pageable pageable);

    List<PurchaseBatch> findByShopAndActiveTrueAndProductIdIn(Shop shop, List<Long> productIds);

    @EntityGraph(attributePaths = { "product", "purchaseEntry" })
    List<PurchaseBatch> findByPurchaseEntryOrderByExpiryDateAscIdAsc(PurchaseEntry purchaseEntry);

    @Query("""
            SELECT b.purchaseEntry.id, COUNT(b)
            FROM PurchaseBatch b
            WHERE b.purchaseEntry.id IN :purchaseEntryIds
            GROUP BY b.purchaseEntry.id
            """)
    List<Object[]> countByPurchaseEntryIds(@Param("purchaseEntryIds") List<Long> purchaseEntryIds);

    List<PurchaseBatch> findByPurchaseEntryAndActiveTrueOrderByExpiryDateAscIdAsc(PurchaseEntry purchaseEntry);

    Optional<PurchaseBatch> findByShopAndProductAndBatchNumberIgnoreCaseAndActiveTrue(
            Shop shop,
            Product product,
            String batchNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM PurchaseBatch b WHERE b.id = :id")
    Optional<PurchaseBatch> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT COALESCE(SUM(b.availableQuantity), 0) FROM PurchaseBatch b WHERE b.product = :product AND b.active = true")
    Integer sumAvailableQuantityByProduct(@Param("product") Product product);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM PurchaseBatch b
            WHERE b.product = :product
            AND b.active = true
            AND (b.expiryDate IS NULL OR b.expiryDate >= :today)
            """)
    Integer sumSellableQuantityByProduct(@Param("product") Product product, @Param("today") LocalDate today);

    @Query("SELECT COALESCE(SUM(b.availableQuantity), 0) FROM PurchaseBatch b WHERE b.shop = :shop AND b.active = true")
    Long sumAvailableQuantityByShop(@Param("shop") Shop shop);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND (b.expiryDate IS NULL OR b.expiryDate >= :today)
            """)
    Long sumSellableQuantityByShop(@Param("shop") Shop shop, @Param("today") LocalDate today);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.purchaseEntry.supplier = :supplier
            AND b.active = true
            AND b.availableQuantity > 0
            """)
    Long sumAvailableQuantityBySupplier(@Param("shop") Shop shop, @Param("supplier") Supplier supplier);

    @Query("""
            SELECT COUNT(DISTINCT b.product.id)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            """)
    long countDistinctProductsWithActiveBatches(@Param("shop") Shop shop);

    @Query("""
            SELECT COUNT(b)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :today AND :cutoff
            """)
    long countNearExpiryBatches(@Param("shop") Shop shop,
                                @Param("today") LocalDate today,
                                @Param("cutoff") LocalDate cutoff);

    @Query("""
            SELECT COUNT(b)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :fromDate AND :toDate
            """)
    long countExpiringBatchesBetween(@Param("shop") Shop shop,
                                     @Param("fromDate") LocalDate fromDate,
                                     @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT COUNT(b)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate < :today
            """)
    long countExpiredBatches(@Param("shop") Shop shop, @Param("today") LocalDate today);

    @Query("""
            SELECT COUNT(b)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.purchaseEntry.supplier = :supplier
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :today AND :cutoff
            """)
    long countNearExpiryBatchesBySupplier(@Param("shop") Shop shop,
                                          @Param("supplier") Supplier supplier,
                                          @Param("today") LocalDate today,
                                          @Param("cutoff") LocalDate cutoff);

    @Query("""
            SELECT COUNT(b)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.purchaseEntry.supplier = :supplier
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate < :today
            """)
    long countExpiredBatchesBySupplier(@Param("shop") Shop shop,
                                       @Param("supplier") Supplier supplier,
                                       @Param("today") LocalDate today);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :fromDate AND :toDate
            """)
    Long sumExpiringQuantityBetween(@Param("shop") Shop shop,
                                    @Param("fromDate") LocalDate fromDate,
                                    @Param("toDate") LocalDate toDate);

    @Query("""
            SELECT COALESCE(SUM(b.availableQuantity), 0)
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate < :today
            """)
    Long sumExpiredQuantity(@Param("shop") Shop shop, @Param("today") LocalDate today);

    @Query("""
            SELECT b
            FROM PurchaseBatch b
            JOIN FETCH b.product p
            JOIN FETCH b.purchaseEntry pe
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :today AND :cutoff
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<PurchaseBatch> findTopNearExpiryBatches(@Param("shop") Shop shop,
                                                 @Param("today") LocalDate today,
                                                 @Param("cutoff") LocalDate cutoff,
                                                 Pageable pageable);

    @Query("""
            SELECT b
            FROM PurchaseBatch b
            JOIN FETCH b.product p
            JOIN FETCH b.purchaseEntry pe
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :fromDate AND :toDate
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<PurchaseBatch> findBatchesExpiringBetween(@Param("shop") Shop shop,
                                                   @Param("fromDate") LocalDate fromDate,
                                                   @Param("toDate") LocalDate toDate,
                                                   Pageable pageable);

    @Query("""
            SELECT b
            FROM PurchaseBatch b
            JOIN FETCH b.product p
            JOIN FETCH b.purchaseEntry pe
            WHERE b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate < :today
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<PurchaseBatch> findExpiredBatches(@Param("shop") Shop shop,
                                           @Param("today") LocalDate today,
                                           Pageable pageable);

    @EntityGraph(attributePaths = { "product", "purchaseEntry" })
    @Query("""
            SELECT b
            FROM PurchaseBatch b
            WHERE b.shop = :shop
            AND b.purchaseEntry.supplier = :supplier
            AND b.active = true
            AND b.availableQuantity > 0
            AND b.expiryDate BETWEEN :today AND :cutoff
            ORDER BY b.expiryDate ASC, b.id ASC
            """)
    List<PurchaseBatch> findTopNearExpiryBatchesBySupplier(@Param("shop") Shop shop,
                                                           @Param("supplier") Supplier supplier,
                                                           @Param("today") LocalDate today,
                                                           @Param("cutoff") LocalDate cutoff,
                                                           Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT b
            FROM PurchaseBatch b
            WHERE b.product.id = :productId
            AND b.shop = :shop
            AND b.active = true
            AND b.availableQuantity > 0
            AND (b.expiryDate IS NULL OR b.expiryDate >= :today)
            ORDER BY CASE WHEN b.expiryDate IS NULL THEN 1 ELSE 0 END,
                     b.expiryDate ASC,
                     b.id ASC
            """)
    List<PurchaseBatch> findAllocatableBatchesForUpdate(@Param("productId") Long productId,
                                                        @Param("shop") Shop shop,
                                                        @Param("today") LocalDate today);
}
