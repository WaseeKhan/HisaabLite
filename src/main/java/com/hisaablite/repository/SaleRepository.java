package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.Shop;



import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Fetch sale with shop to avoid lazy loading issues
    @Query("SELECT s FROM Sale s JOIN FETCH s.shop WHERE s.id = :id")
    Optional<Sale> findByIdWithShop(@Param("id") Long id);

    Page<Sale> findByShop(Shop shop, Pageable pageable);


    //Todays Sales 
    List<Sale> findByShopAndSaleDateBetween(
            Shop shop,
            LocalDateTime start,
            LocalDateTime end
    );

    @Query("SELECT SUM(s.totalAmount) FROM Sale s " +
           "WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end")
    Double getTodayTotalRevenue(@Param("shop") Shop shop,
                                @Param("start") LocalDateTime start,
                                @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(s) FROM Sale s " +
           "WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end")
    Long getTodayInvoiceCount(@Param("shop") Shop shop,
                              @Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);



    //ultra dashboard

   @Query("SELECT DATE(s.saleDate), SUM(s.totalAmount) " +
       "FROM Sale s " +
       "WHERE s.shop = :shop " +
       "AND s.saleDate >= :start " +
       "GROUP BY DATE(s.saleDate) " +
       "ORDER BY DATE(s.saleDate)")
List<Object[]> getLast7DaysRevenue(Shop shop, LocalDateTime start);

    
}