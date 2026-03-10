package com.hisaablite.admin.repository;

import com.hisaablite.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    // Find by user
    List<AuditLog> findByUsernameOrderByTimestampDesc(String username);
    
    // Find by action
    List<AuditLog> findByActionOrderByTimestampDesc(String action);
    
    // Recent activities
    @Query("SELECT a FROM AuditLog a ORDER BY a.timestamp DESC")
    List<AuditLog> findRecentActivities();
    
    // Find by date range
    List<AuditLog> findByTimestampBetweenOrderByTimestampDesc(
        LocalDateTime start, LocalDateTime end);
}