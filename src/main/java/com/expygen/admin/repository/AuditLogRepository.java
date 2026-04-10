package com.expygen.admin.repository;

import com.expygen.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    // Find by user
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
    
    // Find by action
    List<AuditLog> findByActionOrderByTimestampDesc(String action);
    
    // Find by date range
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime start, LocalDateTime end);
    
    // Recent activities (for dashboard)
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentActivities(Pageable pageable);
    
    // Stats by action (last 7 days)
    @Query("SELECT a.action, COUNT(a) as count " +
           "FROM AuditLog a " +
           "WHERE a.timestamp >= :since " +
           "GROUP BY a.action " +
           "ORDER BY count DESC")
    List<Object[]> getActionStats(@Param("since") LocalDateTime since);
    
    // User activity summary
    @Query("SELECT a.username, COUNT(a) as activityCount, MAX(a.timestamp) as lastActive " +
           "FROM AuditLog a " +
           "GROUP BY a.username " +
           "ORDER BY activityCount DESC")
    List<Object[]> getUserActivitySummary(Pageable pageable);
    
    // Failed actions
    List<AuditLog> findByStatusOrderByTimestampDesc(String status);

    @Query("""
           SELECT a FROM AuditLog a
           WHERE (:username IS NULL OR :username = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
             AND (:shopName IS NULL OR :shopName = '' OR LOWER(a.shopName) LIKE LOWER(CONCAT('%', :shopName, '%')))
             AND (:action IS NULL OR :action = '' OR a.action = :action)
             AND (:status IS NULL OR :status = '' OR a.status = :status)
           ORDER BY a.timestamp DESC
           """)
    Page<AuditLog> searchAuditLogs(@Param("username") String username,
            @Param("shopName") String shopName,
            @Param("action") String action,
            @Param("status") String status,
            Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.status = 'FAILED'")
    long countFailedActions();

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.timestamp >= :since")
    long countRecentActions(@Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT a.action FROM AuditLog a ORDER BY a.action")
    List<String> findDistinctActions();

    @Query("SELECT DISTINCT a.shopName FROM AuditLog a WHERE a.shopName IS NOT NULL AND a.shopName <> '' ORDER BY a.shopName")
    List<String> findDistinctShopNames();

    @Query("""
           SELECT a.shopName, COUNT(a)
           FROM AuditLog a
           WHERE a.shopName IS NOT NULL AND a.shopName <> ''
           GROUP BY a.shopName
           ORDER BY COUNT(a) DESC
           """)
    List<Object[]> getShopActivitySummary(Pageable pageable);

    @Query("""
           SELECT a FROM AuditLog a
           WHERE a.shopId = :shopId
             AND (:username IS NULL OR :username = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', :username, '%')))
             AND (:action IS NULL OR :action = '' OR a.action = :action)
             AND (:status IS NULL OR :status = '' OR a.status = :status)
           ORDER BY a.timestamp DESC
           """)
    Page<AuditLog> searchShopAuditLogs(@Param("shopId") Long shopId,
            @Param("username") String username,
            @Param("action") String action,
            @Param("status") String status,
            Pageable pageable);

    long countByShopId(Long shopId);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.shopId = :shopId AND a.status = 'FAILED'")
    long countFailedActionsByShopId(@Param("shopId") Long shopId);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.shopId = :shopId AND a.timestamp >= :since")
    long countRecentActionsByShopId(@Param("shopId") Long shopId, @Param("since") LocalDateTime since);

    @Query("SELECT DISTINCT a.username FROM AuditLog a WHERE a.shopId = :shopId ORDER BY a.username")
    List<String> findDistinctUsernamesByShopId(@Param("shopId") Long shopId);

    @Query("SELECT DISTINCT a.action FROM AuditLog a WHERE a.shopId = :shopId ORDER BY a.action")
    List<String> findDistinctActionsByShopId(@Param("shopId") Long shopId);

    @Query("""
           SELECT a.action, COUNT(a)
           FROM AuditLog a
           WHERE a.shopId = :shopId
             AND a.timestamp >= :since
           GROUP BY a.action
           ORDER BY COUNT(a) DESC
           """)
    List<Object[]> getShopActionStats(@Param("shopId") Long shopId, @Param("since") LocalDateTime since);

    @Query("""
           SELECT a.username, COUNT(a), MAX(a.timestamp)
           FROM AuditLog a
           WHERE a.shopId = :shopId
           GROUP BY a.username
           ORDER BY COUNT(a) DESC
           """)
    List<Object[]> getShopUserActivitySummary(@Param("shopId") Long shopId, Pageable pageable);
}
