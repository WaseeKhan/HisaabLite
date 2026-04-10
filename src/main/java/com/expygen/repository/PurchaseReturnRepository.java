package com.expygen.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expygen.entity.PurchaseReturn;
import com.expygen.entity.Shop;
import com.expygen.entity.Supplier;

public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    Page<PurchaseReturn> findByShopOrderByReturnDateDescIdDesc(Shop shop, Pageable pageable);

    Page<PurchaseReturn> findByShopAndSupplierOrderByReturnDateDescIdDesc(Shop shop, Supplier supplier, Pageable pageable);

    long countByShop(Shop shop);

    long countByShopAndSupplier(Shop shop, Supplier supplier);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM PurchaseReturn r WHERE r.shop = :shop")
    BigDecimal sumTotalAmountByShop(@Param("shop") Shop shop);

    @Query("SELECT COALESCE(SUM(r.totalAmount), 0) FROM PurchaseReturn r WHERE r.shop = :shop AND r.supplier = :supplier")
    BigDecimal sumTotalAmountByShopAndSupplier(@Param("shop") Shop shop, @Param("supplier") Supplier supplier);

    List<PurchaseReturn> findTop5ByShopOrderByReturnDateDescIdDesc(Shop shop);

    @Query("""
            SELECT COALESCE(SUM(line.quantity), 0)
            FROM PurchaseReturnLine line
            JOIN line.purchaseReturn purchaseReturn
            WHERE purchaseReturn.shop = :shop
            AND purchaseReturn.returnDate BETWEEN :startDate AND :endDate
            """)
    Long sumReturnedUnitsByShopAndDateBetween(@Param("shop") Shop shop,
                                              @Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
}
