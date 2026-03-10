package com.hisaablite.admin.repository;

import com.hisaablite.entity.AuditLog;
import org.springframework.data.domain.Pageable;
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
}