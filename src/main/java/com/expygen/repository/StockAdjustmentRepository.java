package com.expygen.repository;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.Shop;
import com.expygen.entity.StockAdjustment;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, Long> {

    Page<StockAdjustment> findByShopOrderByAdjustmentDateDescCreatedAtDesc(Shop shop, Pageable pageable);

    long countByShop(Shop shop);

    @Query("SELECT COALESCE(SUM(a.quantityDelta), 0) FROM StockAdjustment a WHERE a.shop = :shop")
    Long sumQuantityDeltaByShop(@Param("shop") Shop shop);

    @Query("""
            SELECT COALESCE(SUM(CASE WHEN a.quantityDelta < 0 THEN -a.quantityDelta ELSE 0 END), 0)
            FROM StockAdjustment a
            WHERE a.shop = :shop
            AND a.adjustmentDate BETWEEN :startDate AND :endDate
            """)
    Long sumNegativeAdjustedUnitsByShopAndDateBetween(@Param("shop") Shop shop,
                                                      @Param("startDate") LocalDate startDate,
                                                      @Param("endDate") LocalDate endDate);
}
