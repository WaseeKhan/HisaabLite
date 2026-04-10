package com.expygen.repository;

import com.expygen.entity.Sale;
import com.expygen.entity.SaleStatus;
import com.expygen.entity.Shop;
import com.expygen.entity.User;

import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // Fetch sale with shop to avoid lazy loading issues
    @Query("SELECT s FROM Sale s JOIN FETCH s.shop WHERE s.id = :id")
    Optional<Sale> findByIdWithShop(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT DISTINCT s FROM Sale s " +
            "LEFT JOIN FETCH s.items i " +
            "LEFT JOIN FETCH i.product " +
            "WHERE s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    Page<Sale> findByShop(Shop shop, Pageable pageable);

    // ===== ADD THESE METHODS FOR SALES HISTORY =====
    
    // Find by status with pagination (using Enum)
    Page<Sale> findByShopAndStatus(Shop shop, SaleStatus status, Pageable pageable);
    
    // Search sales by keyword with pagination
    @Query("SELECT s FROM Sale s WHERE s.shop = :shop AND " +
           "(CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR " +
           "LOWER(s.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(s.createdBy.name) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Sale> searchSales(@Param("shop") Shop shop, @Param("keyword") String keyword, Pageable pageable);
    
    // Find by date after with pagination
    Page<Sale> findByShopAndSaleDateAfter(Shop shop, LocalDateTime date, Pageable pageable);
    
    // Count by status
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.shop = :shop AND s.status = :status")
    Long countByShopAndStatus(@Param("shop") Shop shop, @Param("status") SaleStatus status);
    
    // Get total revenue for completed sales
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s WHERE s.shop = :shop AND s.status = :status")
    BigDecimal getTotalRevenueByShopAndStatus(@Param("shop") Shop shop, @Param("status") SaleStatus status);
    
    // Default method for total revenue
    default BigDecimal getTotalRevenueByShop(Shop shop) {
        return getTotalRevenueByShopAndStatus(shop, SaleStatus.COMPLETED);
    }

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
                    "GROUP BY FUNCTION('DATE', s.saleDate) " +
                    "ORDER BY FUNCTION('DATE', s.saleDate)")
    List<Object[]> getLast7DaysRevenue(Shop shop, LocalDateTime start);

    // cancellation related customs
    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s " +
                    "WHERE s.shop = :shop " +
                    "AND s.saleDate BETWEEN :start AND :end " +
                    "AND s.status = 'COMPLETED'")
    Double getTodayCompletedRevenue(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(s.totalAmount), 0) FROM Sale s " +
                    "WHERE s.shop = :shop " +
                    "AND s.saleDate BETWEEN :start AND :end " +
                    "AND s.status = 'CANCELLED'")
    Double getTodayCancelledAmount(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(CASE WHEN s.status = 'COMPLETED' THEN s.totalAmount ELSE 0 END), 0) - " +
                    "COALESCE(SUM(CASE WHEN s.status = 'CANCELLED' THEN s.totalAmount ELSE 0 END), 0) " +
                    "FROM Sale s WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end")
    Double getTodayNetRevenue(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(si.quantity), 0) FROM SaleItem si " +
                    "WHERE si.sale.shop = :shop " +
                    "AND si.sale.saleDate BETWEEN :start AND :end " +
                    "AND si.sale.status = 'CANCELLED'")
    Long getTodayReturnedItems(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    long countByShop(Shop shop);

    @Modifying
    @Transactional
    @Query("UPDATE Sale s SET s.createdBy = :newUser WHERE s.createdBy = :oldUser")
    int reassignSales(@Param("oldUser") User oldUser, @Param("newUser") User newUser);

    Long countByCreatedBy(User user);
    
    // Get recent completed sales using Enum
    @Query("SELECT s FROM Sale s WHERE s.shop = :shop AND s.status = :status ORDER BY s.saleDate DESC")
    List<Sale> findRecentSalesByShopAndStatus(@Param("shop") Shop shop, @Param("status") SaleStatus status, Pageable pageable);
    
    // Spring Data JPA method with Enum - This will work
    List<Sale> findTop10ByShopAndStatusOrderBySaleDateDesc(Shop shop, SaleStatus status);
    
    // Get top customers by total spent
    @Query("""
        SELECT COALESCE(s.customerName, 'Walk-in Customer') as name, 
               COUNT(s.id) as order_count, 
               SUM(s.totalAmount) as total_spent
        FROM Sale s
        WHERE s.shop = :shop AND s.status = 'COMPLETED'
        GROUP BY s.customerName
        ORDER BY total_spent DESC
    """)
    List<Object[]> findTopCustomersByShop(@Param("shop") Shop shop, Pageable pageable);

    // ===== ADD THESE METHODS FOR DASHBOARD CARDS =====
    
    // Get today's total sales count (all sales including completed and cancelled)
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end")
    Long getTodaySalesCount(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);
    
    // Get today's completed sales count
    @Query("SELECT COUNT(s) FROM Sale s WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end AND s.status = 'COMPLETED'")
    Long getTodayCompletedCount(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);
    
    // Get today's unique customers count
    @Query("SELECT COUNT(DISTINCT s.customerName) FROM Sale s WHERE s.shop = :shop AND s.saleDate BETWEEN :start AND :end AND s.customerName IS NOT NULL AND s.customerName != ''")
    Long getTodayUniqueCustomers(@Param("shop") Shop shop,
                    @Param("start") LocalDateTime start,
                    @Param("end") LocalDateTime end);

    // ===== LIFETIME BUSINESS DATA METHODS =====

// Count distinct customers for a shop
@Query("SELECT COUNT(DISTINCT s.customerName) FROM Sale s WHERE s.shop = :shop AND s.customerName IS NOT NULL AND s.customerName != ''")
Long countDistinctCustomersByShop(@Param("shop") Shop shop);











@Query("""
    SELECT COALESCE(SUM(s.totalAmount), 0)
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
      AND s.status = 'COMPLETED'
""")
java.math.BigDecimal getSalesSummaryTotalSales(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword
);

@Query("""
    SELECT COUNT(s)
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
      AND s.status = 'COMPLETED'
""")
Long getSalesSummaryInvoiceCount(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword
);

@Query("""
    SELECT COALESCE(SUM(s.totalGstAmount), 0)
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
      AND s.status = 'COMPLETED'
""")
java.math.BigDecimal getSalesSummaryTotalGst(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword
);

@Query("""
    SELECT COALESCE(SUM(s.discountAmount), 0)
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
      AND s.status = 'COMPLETED'
""")
java.math.BigDecimal getSalesSummaryTotalDiscount(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword
);

@Query("""
    SELECT COUNT(s)
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
      AND s.status = 'CANCELLED'
""")
Long getSalesSummaryCancelledBills(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword
);

@Query("""
    SELECT s
    FROM Sale s
    WHERE s.shop = :shop
      AND (:fromDate IS NULL OR s.saleDate >= :fromDate)
      AND (:toDate IS NULL OR s.saleDate <= :toDate)
      AND (:paymentMode IS NULL OR :paymentMode = '' OR s.paymentMode = :paymentMode)
      AND (
            :keyword IS NULL OR :keyword = '' OR
            CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR
            LOWER(COALESCE(s.customerName, '')) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
            LOWER(COALESCE(s.customerPhone, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
          )
    ORDER BY s.saleDate DESC
""")
List<Sale> findSalesSummaryRows(
        @Param("shop") Shop shop,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("paymentMode") String paymentMode,
        @Param("keyword") String keyword,
        Pageable pageable
);



List<Sale> findByShopAndSaleDateBetweenOrderBySaleDateDesc(
        Shop shop,
        LocalDateTime startDate,
        LocalDateTime endDate
);




}







