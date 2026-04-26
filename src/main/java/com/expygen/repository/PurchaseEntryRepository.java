package com.expygen.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.PurchaseEntry;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;

public interface PurchaseEntryRepository extends JpaRepository<PurchaseEntry, Long> {

    @Query("""
            SELECT DISTINCT p
            FROM PurchaseEntry p
            LEFT JOIN FETCH p.supplier supplier
            LEFT JOIN FETCH p.createdBy createdBy
            LEFT JOIN FETCH p.batches batch
            LEFT JOIN FETCH batch.product product
            WHERE p.shop = :shop
            """)
    List<PurchaseEntry> findAllWithInsightsRelationsByShop(@Param("shop") Shop shop);

    @EntityGraph(attributePaths = { "supplier", "createdBy" })
    Page<PurchaseEntry> findByShopOrderByPurchaseDateDescIdDesc(Shop shop, Pageable pageable);

    @EntityGraph(attributePaths = { "supplier", "createdBy" })
    Page<PurchaseEntry> findByShopAndSupplierOrderByPurchaseDateDescIdDesc(Shop shop, Supplier supplier, Pageable pageable);

    @EntityGraph(attributePaths = { "supplier", "createdBy" })
    java.util.Optional<PurchaseEntry> findByIdAndShop(Long id, Shop shop);

    long countByShop(Shop shop);

    long countByShopAndSupplier(Shop shop, Supplier supplier);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseEntry p WHERE p.shop = :shop")
    BigDecimal sumTotalAmountByShop(@Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseEntry p WHERE p.shop = :shop AND p.supplier = :supplier")
    BigDecimal sumTotalAmountByShopAndSupplier(@Param("shop") Shop shop, @Param("supplier") Supplier supplier);
}
