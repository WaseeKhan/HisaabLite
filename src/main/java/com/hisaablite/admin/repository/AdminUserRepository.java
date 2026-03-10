package com.hisaablite.admin.repository;

import com.hisaablite.entity.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;  // Fixed import - should be from spring.data.domain
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface AdminUserRepository extends JpaRepository<User, Long> {
    
    long countByApprovedFalse();
    long countByActiveTrue();

    Page<User> findAll(Pageable pageable);  // This will work with correct Pageable
    
    @Query("SELECT u FROM User u WHERE " +
           "LOWER(u.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.username) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.phone) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
    
    @Query("SELECT u FROM User u JOIN FETCH u.shop ORDER BY u.createdAt DESC")
    List<User> findAllUsersWithShop();
    
    List<User> findByRole(String role);
    
    @Query("SELECT DATE(u.createdAt) as date, COUNT(u) as count " +
           "FROM User u " +
           "WHERE u.createdAt BETWEEN :start AND :end " +
           "GROUP BY DATE(u.createdAt) " +
           "ORDER BY DATE(u.createdAt)")
    List<Object[]> getUserStatsByDate(
        @Param("start") LocalDateTime start, 
        @Param("end") LocalDateTime end
    );
    
    @Query("SELECT " +
           "COUNT(u) as totalUsers, " +
           "SUM(CASE WHEN u.active = true THEN 1 ELSE 0 END) as activeUsers, " +
           "SUM(CASE WHEN u.approved = false THEN 1 ELSE 0 END) as pendingApprovals " +
           "FROM User u")
    Map<String, Object> getUserDashboardStats();
    
    @Query("SELECT u FROM User u ORDER BY u.createdAt DESC LIMIT 10")
    List<User> findTop5ByOrderByCreatedAtDesc();
}