package com.hisaablite.repository;

import com.hisaablite.entity.Sale;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;

import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

       // Todays Sales
       List<Sale> findByShopAndSaleDateBetween(
                     Shop shop,
                     LocalDateTime start,
                     LocalDateTime end);

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

       // ultra dashboard

       @Query("SELECT DATE(s.saleDate), SUM(s.totalAmount) " +
                     "FROM Sale s " +
                     "WHERE s.shop = :shop " +
                     "AND s.saleDate >= :start " +
                     "GROUP BY DATE(s.saleDate) " +
                     "ORDER BY DATE(s.saleDate)")
       List<Object[]> getLast7DaysRevenue(Shop shop, LocalDateTime start);

       // cacellation realated custms

       // Today's COMPLETED sales revenue (actual income)
       @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s " +
                     "WHERE s.shop = :shop " +
                     "AND DATE(s.saleDate) = CURRENT_DATE " +
                     "AND s.status = 'COMPLETED'")
       Double getTodayCompletedRevenue(@Param("shop") Shop shop);

       // Today's CANCELLED sales amount (refunds/returns)
       @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s " +
                     "WHERE s.shop = :shop " +
                     "AND DATE(s.saleDate) = CURRENT_DATE " +
                     "AND s.status = 'CANCELLED'")
       Double getTodayCancelledAmount(@Param("shop") Shop shop);

       // Today's Net Revenue (Completed - Cancelled)
       @Query("SELECT COALESCE(SUM(CASE WHEN s.status = 'COMPLETED' THEN s.totalAmount ELSE 0 END), 0) - " +
                     "COALESCE(SUM(CASE WHEN s.status = 'CANCELLED' THEN s.totalAmount ELSE 0 END), 0) " +
                     "FROM Sale s WHERE s.shop = :shop AND DATE(s.saleDate) = CURRENT_DATE")
       Double getTodayNetRevenue(@Param("shop") Shop shop);

       // Today's returned items count
       @Query("SELECT COALESCE(SUM(si.quantity), 0) FROM SaleItem si " +
                     "WHERE si.sale.shop = :shop " +
                     "AND DATE(si.sale.saleDate) = CURRENT_DATE " +
                     "AND si.sale.status = 'CANCELLED'")
       Long getTodayReturnedItems(@Param("shop") Shop shop);

       long countByShop(Shop shop);

       @Modifying
       @Transactional
       @Query("UPDATE Sale s SET s.createdBy = :newUser WHERE s.createdBy = :oldUser")
       int reassignSales(@Param("oldUser") User oldUser, @Param("newUser") User newUser);

       Long countByCreatedBy(User user);

}