package com.hisaablite.admin.service;

import com.hisaablite.entity.AuditLog;

import jakarta.servlet.http.HttpServletRequest;

import com.hisaablite.admin.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    // Simple log method
    public void logAction(String username, String userRole, String action, 
                         String entityType, Long entityId, String status) {
        logAction(username, userRole, action, entityType, entityId, status, null, null, null);
    }

    // Main log method with all details
    public void logAction(String username, String userRole, String action, 
                         String entityType, Long entityId, String status,
                         Object oldValue, Object newValue, String additionalDetails) {
        
        try {
            AuditLog.AuditLogBuilder builder = AuditLog.builder()
                .username(username)
                .userRole(userRole)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .status(status)
                .timestamp(LocalDateTime.now())
                .ipAddress(getClientIp());  // ✅ Yahan use ho raha hai

            // Convert old/new values to JSON
            if (oldValue != null) {
                builder.oldValue(objectMapper.writeValueAsString(oldValue));
            }
            if (newValue != null) {
                builder.newValue(objectMapper.writeValueAsString(newValue));
            }

            // Additional details as JSON
            if (additionalDetails != null) {
                builder.details(additionalDetails);
            } else if (oldValue != null || newValue != null) {
                Map<String, Object> details = new HashMap<>();
                details.put("old", oldValue);
                details.put("new", newValue);
                builder.details(objectMapper.writeValueAsString(details));
            }

            AuditLog auditLog = builder.build();
            auditLogRepository.save(auditLog);
            
            log.info("Audit log created: {} - {} by {}", action, entityType, username);
            
        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage());
        }
    }

    /**
     * Get client IP address from request
     * Handles proxy servers and forwarding
     */
    private String getClientIp() {
        try {
            // Get current HTTP request
            ServletRequestAttributes attributes = (ServletRequestAttributes) 
                RequestContextHolder.currentRequestAttributes();
            
            HttpServletRequest request = attributes.getRequest();
            
            // Check for proxy headers (in order)
            String ip = request.getHeader("X-Forwarded-For");
            
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();  // Fallback to remote address
            }
            
            // Handle multiple IPs in X-Forwarded-For (take first)
            if (ip != null && ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            
            return ip;
            
        } catch (IllegalStateException e) {
            // No request context available (e.g., in background threads)
            log.debug("No request context available for IP lookup");
            return "SYSTEM";
        } catch (Exception e) {
            log.error("Error getting client IP: {}", e.getMessage());
            return "UNKNOWN";
        }
    }

    /**
     * Get current username from security context
     * (Optional helper method)
     */
    private String getCurrentUsername() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    /**
     * Get current user role from security context
     * (Optional helper method)
     */
    private String getCurrentUserRole() {
        try {
            return org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getAuthorities()
                .iterator()
                .next()
                .getAuthority();
        } catch (Exception e) {
            return "SYSTEM";
        }
    }

    // Convenience methods for common actions
    
    public void logLogin(String username, String role, boolean success) {
        logAction(
            username,
            role,
            "LOGIN",
            "AUTH",
            null,
            success ? "SUCCESS" : "FAILED",
            null,
            null,
            "User login attempt"
        );
    }

    public void logLogout(String username, String role) {
        logAction(
            username,
            role,
            "LOGOUT",
            "AUTH",
            null,
            "SUCCESS",
            null,
            null,
            "User logged out"
        );
    }

    public void logCreate(String username, String role, String entityType, 
                         Long entityId, Object entity) {
        logAction(
            username,
            role,
            "CREATE_" + entityType.toUpperCase(),
            entityType,
            entityId,
            "SUCCESS",
            null,
            entity,
            entityType + " created"
        );
    }

    public void logUpdate(String username, String role, String entityType,
                         Long entityId, Object oldValue, Object newValue) {
        logAction(
            username,
            role,
            "UPDATE_" + entityType.toUpperCase(),
            entityType,
            entityId,
            "SUCCESS",
            oldValue,
            newValue,
            entityType + " updated"
        );
    }

    public void logDelete(String username, String role, String entityType,
                         Long entityId, Object deletedEntity) {
        logAction(
            username,
            role,
            "DELETE_" + entityType.toUpperCase(),
            entityType,
            entityId,
            "SUCCESS",
            deletedEntity,
            null,
            entityType + " deleted"
        );
    }

    public void logError(String username, String role, String action,
                        String entityType, Long entityId, String errorMessage) {
        logAction(
            username,
            role,
            action,
            entityType,
            entityId,
            "FAILED",
            null,
            null,
            "Error: " + errorMessage
        );
    }
}