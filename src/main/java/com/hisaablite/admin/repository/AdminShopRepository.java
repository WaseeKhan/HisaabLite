package com.hisaablite.admin.repository;

import com.hisaablite.entity.Shop;
import com.hisaablite.entity.PlanType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface AdminShopRepository extends JpaRepository<Shop, Long> {
    
    // ===== BASIC FINDERS =====
    List<Shop> findByActive(boolean active);
    long countByActive(boolean active);
    long countByPlanType(PlanType planType);
    List<Shop> findByPlanType(PlanType planType);
    
    // ===== PAN VALIDATION =====
    boolean existsByPanNumber(String panNumber);
    Optional<Shop> findByPanNumber(String panNumber);
    
    // ===== GST VALIDATION =====
    boolean existsByGstNumber(String gstNumber);
    Optional<Shop> findByGstNumber(String gstNumber);
    
    // ===== STATISTICS =====
    @Query("SELECT s.planType, COUNT(s) FROM Shop s GROUP BY s.planType")
    List<Object[]> countShopsByPlanType();
    
    @Query("SELECT " +
           "COUNT(s) as totalShops, " +
           "SUM(CASE WHEN s.active = true THEN 1 ELSE 0 END) as activeShops, " +
           "SUM(CASE WHEN s.active = false THEN 1 ELSE 0 END) as inactiveShops " +
           "FROM Shop s")
    Map<String, Object> getShopDashboardStats();
    
    
    @Query("SELECT s FROM Shop s ORDER BY s.createdAt DESC")
    List<Shop> findTop5ByOrderByCreatedAtDesc(Pageable pageable);
    
    @Query("SELECT s FROM Shop s WHERE s.active = true ORDER BY s.createdAt DESC")
    List<Shop> findTop5ActiveShops(Pageable pageable);
    
   
    default List<Shop> findTop5ByOrderByCreatedAtDesc() {
        return findTop5ByOrderByCreatedAtDesc(PageRequest.of(0, 5));
    }
    
    default List<Shop> findTop5ActiveShops() {
        return findTop5ActiveShops(PageRequest.of(0, 5));
    }
    
    // ===== SEARCH =====
    @Query("SELECT s FROM Shop s WHERE " +
           "LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.city) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.state) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Shop> searchShops(@Param("search") String search, Pageable pageable);
    
    // ===== DEFAULT METHODS WITH ERROR HANDLING =====
    default Map<String, Object> getSafeShopStats() {
        try {
            return getShopDashboardStats();
        } catch (Exception e) {
            Map<String, Object> stats = new HashMap<>();
            stats.put("totalShops", count());
            stats.put("activeShops", countByActive(true));
            stats.put("inactiveShops", countByActive(false));
            return stats;
        }
    }
    
    default Map<String, Long> getPlanStatistics() {
        Map<String, Long> planStats = new HashMap<>();
        try {
            List<Object[]> results = countShopsByPlanType();
            for (Object[] row : results) {
                PlanType planType = (PlanType) row[0];
                Long count = (Long) row[1];
                planStats.put(planType.name(), count);
            }
            
          
            for (PlanType plan : PlanType.values()) {
                planStats.putIfAbsent(plan.name(), 0L);
            }
        } catch (Exception e) {
            for (PlanType plan : PlanType.values()) {
                planStats.put(plan.name(), 0L);
            }
        }
        return planStats;
    }
}