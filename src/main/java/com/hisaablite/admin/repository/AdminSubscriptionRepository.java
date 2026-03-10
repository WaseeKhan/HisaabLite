package com.hisaablite.admin.repository;

import com.hisaablite.entity.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdminSubscriptionRepository extends JpaRepository<SubscriptionPlan, Long> {
    
    Optional<SubscriptionPlan> findByPlanName(String planName);
    
    List<SubscriptionPlan> findByActiveTrue();
    
    // Get plan usage statistics by joining with shops table
    @Query("SELECT sp.planName, COUNT(s) FROM SubscriptionPlan sp " +
           "LEFT JOIN Shop s ON s.planType = sp.planName " +
           "GROUP BY sp.planName")
    List<Object[]> getPlanUsageStats();
}