package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.Shop;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale(Sale sale);

    // Todays sales dashboard
    @Query("SELECT SUM(si.quantity) FROM SaleItem si " +
            "WHERE si.sale.shop = :shop " +
            "AND si.sale.saleDate BETWEEN :start AND :end")
    Long getTodayItemsSold(@Param("shop") Shop shop,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
    
    // Get top selling products by quantity
    @Query("""
        SELECT p.id, p.name, SUM(si.quantity) as total_quantity, SUM(si.totalWithGst) as total_revenue
        FROM SaleItem si
        JOIN si.product p
        JOIN si.sale s
        WHERE s.shop = :shop 
        AND s.status = 'COMPLETED'
        GROUP BY p.id, p.name
        ORDER BY total_quantity DESC
    """)
    List<Object[]> findTopSellingProductsByShop(@Param("shop") Shop shop, Pageable pageable);


    // ===== LIFETIME BUSINESS DATA METHODS =====

// Get total items sold for a shop (all time)
@Query("SELECT COALESCE(SUM(si.quantity), 0) FROM SaleItem si WHERE si.sale.shop = :shop AND si.sale.status = 'COMPLETED'")
Long getTotalItemsSoldByShop(@Param("shop") Shop shop);


}
