package com.hisaablite.repository;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hisaablite.entity.PurchaseEntry;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.Supplier;

public interface PurchaseEntryRepository extends JpaRepository<PurchaseEntry, Long> {

    Page<PurchaseEntry> findByShopOrderByPurchaseDateDescIdDesc(Shop shop, Pageable pageable);

    Page<PurchaseEntry> findByShopAndSupplierOrderByPurchaseDateDescIdDesc(Shop shop, Supplier supplier, Pageable pageable);

    java.util.Optional<PurchaseEntry> findByIdAndShop(Long id, Shop shop);

    long countByShop(Shop shop);

    long countByShopAndSupplier(Shop shop, Supplier supplier);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseEntry p WHERE p.shop = :shop")
    BigDecimal sumTotalAmountByShop(@Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(p.totalAmount), 0) FROM PurchaseEntry p WHERE p.shop = :shop AND p.supplier = :supplier")
    BigDecimal sumTotalAmountByShopAndSupplier(@Param("shop") Shop shop, @Param("supplier") Supplier supplier);
}
