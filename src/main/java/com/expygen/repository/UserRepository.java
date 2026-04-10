package com.expygen.repository;

import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username); // REQUIRED FOR LOGIN AS EMAIL IS USERNAME

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    Long countByShop(Shop shop);

    Long countByShopAndRole(Shop shop, Role role);

    List<User> findByShop(Shop shop);

    Optional<User> findByPhone(String phone);



/**
 * Find users approved within a date range
 */
@Query("SELECT u FROM User u WHERE u.approved = true AND u.approvalDate BETWEEN :start AND :end")
List<User> findByApprovalDateBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

/**
 * Find users approved after a specific date
 */
List<User> findByApprovedTrueAndApprovalDateAfter(LocalDateTime date);

/**
 * Find users with subscription ending between dates
 */
List<User> findBySubscriptionEndDateBetween(LocalDateTime start, LocalDateTime end);

/**
 * Find users with subscription ending before date (already expired)
 */
List<User> findBySubscriptionEndDateBeforeAndApprovedTrue(LocalDateTime date);

/**
 * Find users with expiring subscription (for reminders)
 */
@Query("SELECT u FROM User u WHERE u.subscriptionEndDate BETWEEN :start AND :end AND u.approved = true")
List<User> findUsersWithExpiringSubscription(@Param("start") LocalDateTime start, 
                                             @Param("end") LocalDateTime end);

/**
 * Count users by plan type
 */
@Query("SELECT u.currentPlan, COUNT(u) FROM User u WHERE u.approved = true GROUP BY u.currentPlan")
List<Object[]> countUsersByPlanType();

/**
 * Get users who are active but not approved (pending approval)
 */
@Query("SELECT u FROM User u WHERE u.active = true AND u.approved = false")
List<User> findPendingApprovalUsers();

/**
 * Get users by plan and approval status
 */
List<User> findByCurrentPlanAndApprovedTrue(PlanType planType);

}