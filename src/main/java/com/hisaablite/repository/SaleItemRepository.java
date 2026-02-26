package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.SaleItem;
import com.hisaablite.entity.Shop;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    List<SaleItem> findBySale(Sale sale);

    //Todays sales dashboard
     @Query("SELECT SUM(si.quantity) FROM SaleItem si " +
           "WHERE si.sale.shop = :shop " +
           "AND si.sale.saleDate BETWEEN :start AND :end")
    Long getTodayItemsSold(@Param("shop") Shop shop,
                           @Param("start") LocalDateTime start,
                           @Param("end") LocalDateTime end);
}