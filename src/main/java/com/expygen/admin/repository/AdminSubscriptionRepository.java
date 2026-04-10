package com.expygen.admin.repository;

import com.expygen.entity.SubscriptionPlan;
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




    // AdminSubscriptionRepository for debug
@Query(value = "SELECT * FROM subscription_plans", nativeQuery = true)
List<Object[]> findAllRaw();

@Query(value = "SELECT COUNT(*) FROM subscription_plans WHERE active = 1", nativeQuery = true)
int countActivePlansRaw();
}