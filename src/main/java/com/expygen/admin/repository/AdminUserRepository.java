package com.expygen.admin.repository;

import com.expygen.entity.User;
import com.expygen.entity.Shop;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {
    
    // ===== BASIC COUNTS =====
    long countByApprovedFalse();
    long countByActiveTrue();
    
    // ===== EXISTENCE CHECKS =====
    boolean existsByUsername(String username);
    boolean existsByPhone(String phone);
    
    // ===== FIND BY USERNAME =====
    Optional<User> findByUsername(String username);
    
    // ===== PAGINATION =====
    Page<User> findAll(Pageable pageable);
    
    // ===== SEARCH =====
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
    
    // ===== FETCH WITH SHOP =====
    @Query("SELECT u FROM User u JOIN FETCH u.shop ORDER BY u.createdAt DESC")
    List<User> findAllUsersWithShop();
    
    // ===== FIND BY ROLE =====
    List<User> findByRole(Role role);

     // ===== PENDING APPROVALS =====
    Page<User> findByActiveTrueAndApprovedFalse(Pageable pageable);
    
    List<User> findByActiveTrueAndApprovedFalseOrderByCreatedAtAsc();
    
     // ===== FIND BY PLAN TYPE =====
    List<User> findByCurrentPlanAndApprovedTrue(PlanType planType);
    


    // ===== EXPIRY RELATED =====
    @Query("SELECT u FROM User u WHERE u.subscriptionEndDate BETWEEN :start AND :end AND u.approved = true")
    List<User> findUsersWithExpiringSubscription(@Param("start") LocalDateTime start, 
                                                 @Param("end") LocalDateTime end);
    
    List<User> findBySubscriptionEndDateBeforeAndApprovedTrue(LocalDateTime now);
    


    
    // ===== STATISTICS BY DATE =====
    @Query("SELECT FUNCTION('DATE', u.createdAt) as date, COUNT(u) as count " +
           "FROM User u " +
           "WHERE u.createdAt BETWEEN :start AND :end " +
           "GROUP BY FUNCTION('DATE', u.createdAt) " +
           "ORDER BY FUNCTION('DATE', u.createdAt)")
    List<Object[]> getUserStatsByDate(
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );
    
    // ===== DASHBOARD STATS =====
    @Query("SELECT " +
           "COUNT(u) as totalUsers, " +
           "SUM(CASE WHEN u.active = true THEN 1 ELSE 0 END) as activeUsers, " +
           "SUM(CASE WHEN u.approved = false THEN 1 ELSE 0 END) as pendingApprovals " +
           "FROM User u")
    Map<String, Object> getUserDashboardStats();
    
   
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC")
    List<User> findTop10ByOrderByCreatedAtDesc(Pageable pageable);
   
    default List<User> findTop5ByOrderByCreatedAtDesc() {
        return findTop10ByOrderByCreatedAtDesc(PageRequest.of(0, 5));
    }
    
    // ===== FIND BY SHOP =====
    List<User> findByShop(Shop shop);
    
    // ===== COUNT BY SHOP AND ROLE =====
    long countByShopAndRole(Shop shop, Role role);

     // ===== PLAN STATISTICS =====
    @Query("SELECT u.currentPlan, COUNT(u) FROM User u WHERE u.approved = true GROUP BY u.currentPlan")
    List<Object[]> countUsersByPlanType();

    @Query("SELECT COUNT(u) FROM User u WHERE u.active = true AND u.approved = false")
    long countByActiveTrueAndApprovedFalse();

    @Query("SELECT u FROM User u WHERE u.active = true AND u.approved = false AND " +
       "(LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
       "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
       "LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%')))")
Page<User> searchPendingUsers(@Param("search") String search, Pageable pageable);
    
}
