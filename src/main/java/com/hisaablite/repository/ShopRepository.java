package com.hisaablite.repository;

import com.hisaablite.entity.Shop;
import com.hisaablite.entity.PlanType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShopRepository extends JpaRepository<Shop, Long> {

    // ===== EXISTING METHODS =====
    boolean existsByPanNumber(String panNumber);
    Optional<Shop> findByPanNumber(String panNumber);
    
    // ===== ADD THESE NEW METHODS =====
    
    /**
     * Find shop by GST number
     */
    Optional<Shop> findByGstNumber(String gstNumber);
    
    /**
     * Check if GST number exists
     */
    boolean existsByGstNumber(String gstNumber);
    
    /**
     * Find shops by plan type
     */
    List<Shop> findByPlanType(PlanType planType);
    
    /**
     * Find shops by active status
     */
    List<Shop> findByActive(boolean active);
    
    /**
     * Count shops by plan type
     */
    long countByPlanType(PlanType planType);
    
    /**
     * Count shops by active status
     */
    long countByActive(boolean active);
    
    /**
     * Get shop counts grouped by plan type (for dashboard)
     */
    @Query("SELECT s.planType, COUNT(s) FROM Shop s GROUP BY s.planType")
    List<Object[]> countShopsByPlanType();
    
    /**
     * Find shops with expired subscription
     */
    @Query("SELECT s FROM Shop s WHERE s.subscriptionEndDate < :date AND s.active = true")
    List<Shop> findShopsWithExpiredSubscription(@Param("date") LocalDateTime date);
    
    /**
     * Find shops with subscription expiring between dates
     */
    @Query("SELECT s FROM Shop s WHERE s.subscriptionEndDate BETWEEN :start AND :end")
    List<Shop> findShopsWithExpiringSubscription(@Param("start") LocalDateTime start, 
                                                  @Param("end") LocalDateTime end);
    
    /**
     * Find shops by city
     */
    List<Shop> findByCity(String city);
    
    /**
     * Find shops by state
     */
    List<Shop> findByState(String state);
    
    /**
     * Search shops by name or city
     */
    @Query("SELECT s FROM Shop s WHERE " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.state) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<Shop> searchShops(@Param("search") String search);
    
    /**
     * Get total revenue by plan type (if you have revenue field)
     */
    // @Query("SELECT s.planType, SUM(s.totalRevenue) FROM Shop s GROUP BY s.planType")
    // List<Object[]> getRevenueByPlanType();
    
    /**
     * Find recently created shops
     */
    List<Shop> findTop10ByOrderByCreatedAtDesc();
    
    /**
     * Find shops created after date
     */
    List<Shop> findByCreatedAtAfter(LocalDateTime date);
    
    /**
     * Get shop statistics for dashboard
     */
    @Query("SELECT " +
           "COUNT(s) as totalShops, " +
           "SUM(CASE WHEN s.active = true THEN 1 ELSE 0 END) as activeShops, " +
           "SUM(CASE WHEN s.planType = 'FREE' THEN 1 ELSE 0 END) as freePlanShops, " +
           "SUM(CASE WHEN s.planType = 'BASIC' THEN 1 ELSE 0 END) as basicPlanShops, " +
           "SUM(CASE WHEN s.planType = 'PREMIUM' THEN 1 ELSE 0 END) as premiumPlanShops, " +
           "SUM(CASE WHEN s.planType = 'ENTERPRISE' THEN 1 ELSE 0 END) as enterprisePlanShops " +
           "FROM Shop s")
    List<Object[]> getShopStatistics();
    
    /**
     * Default method to check if shop has valid subscription
     */
    default boolean hasValidSubscription(Shop shop) {
        if (shop.getSubscriptionEndDate() == null) return true; // FREE or lifetime plan
        return LocalDateTime.now().isBefore(shop.getSubscriptionEndDate());
    }
    
    /**
     * Default method to get days until expiry
     */
    default Long getDaysUntilExpiry(Shop shop) {
        if (shop.getSubscriptionEndDate() == null) return null;
        return java.time.Duration.between(LocalDateTime.now(), shop.getSubscriptionEndDate()).toDays();
    }
}