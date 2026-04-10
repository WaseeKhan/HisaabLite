package com.expygen.repository;

import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.PlanType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPlanRepository extends JpaRepository<SubscriptionPlan, Long> {
    
    // Find by plan name (case insensitive)
    Optional<SubscriptionPlan> findByPlanName(String planName);
    
    // Find by plan name ignoring case
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE UPPER(sp.planName) = UPPER(:planName)")
    Optional<SubscriptionPlan> findByPlanNameIgnoreCase(@Param("planName") String planName);
    
    // Find by PlanType enum (converted to string)
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE UPPER(sp.planName) = UPPER(:planType)")
    Optional<SubscriptionPlan> findByPlanType(@Param("planType") PlanType planType);
    
    // Find all active plans
    List<SubscriptionPlan> findByActiveTrue();
    
    // Find all active plans ordered by price
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.active = true ORDER BY sp.price ASC")
    List<SubscriptionPlan> findActivePlansOrderedByPrice();
    
    // Find plans with duration less than or equal to specified days
    List<SubscriptionPlan> findByDurationInDaysLessThanEqual(Integer days);
    
    // Find plans by price range
    List<SubscriptionPlan> findByPriceBetween(Double minPrice, Double maxPrice);
    
    // Check if plan name exists (excluding specific id)
    @Query("SELECT CASE WHEN COUNT(sp) > 0 THEN true ELSE false END FROM SubscriptionPlan sp " +
           "WHERE UPPER(sp.planName) = UPPER(:planName) AND sp.id != :id")
    boolean existsByPlanNameAndIdNot(@Param("planName") String planName, @Param("id") Long id);
    
    // Get basic plan info for dropdowns
    @Query("SELECT new map(sp.planName as planName, sp.price as price, sp.durationInDays as duration) " +
           "FROM SubscriptionPlan sp WHERE sp.active = true")
    List<Object[]> getActivePlanBasicInfo();
    
    // Find plans created after date
    List<SubscriptionPlan> findByCreatedAtAfter(LocalDateTime date);
    
    // Find plans updated after date
    List<SubscriptionPlan> findByUpdatedAtAfter(LocalDateTime date);
    
    // Count active plans
    long countByActiveTrue();
    
    // Get plan with maximum users
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.maxUsers = (SELECT MAX(sp2.maxUsers) FROM SubscriptionPlan sp2)")
    Optional<SubscriptionPlan> findPlanWithMaxUsers();
    
    // Get plan with minimum price (excluding FREE)
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE sp.price > 0 ORDER BY sp.price ASC")
    List<SubscriptionPlan> findPaidPlansAsc();
    
    // Search plans by name or description
    @Query("SELECT sp FROM SubscriptionPlan sp WHERE " +
           "LOWER(sp.planName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(sp.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    List<SubscriptionPlan> searchPlans(@Param("search") String search);
    
    // Get plan statistics for dashboard
    @Query("SELECT " +
           "COUNT(sp) as totalPlans, " +
           "SUM(CASE WHEN sp.active = true THEN 1 ELSE 0 END) as activePlans, " +
           "AVG(sp.price) as avgPrice, " +
           "MAX(sp.price) as maxPrice, " +
           "MIN(CASE WHEN sp.price > 0 THEN sp.price ELSE NULL END) as minPaidPrice " +
           "FROM SubscriptionPlan sp")
    List<Object[]> getPlanStatistics();
    
    // Default method to get FREE plan
    default Optional<SubscriptionPlan> getFreePlan() {
        return findByPlanNameIgnoreCase("FREE");
    }
    
    // Default method to get BASIC plan
    default Optional<SubscriptionPlan> getBasicPlan() {
        return findByPlanNameIgnoreCase("BASIC");
    }
    
    // Default method to get PREMIUM plan
    default Optional<SubscriptionPlan> getPremiumPlan() {
        return findByPlanNameIgnoreCase("PREMIUM");
    }
    
    // Default method to get ENTERPRISE plan
    default Optional<SubscriptionPlan> getEnterprisePlan() {
        return findByPlanNameIgnoreCase("ENTERPRISE");
    }
}