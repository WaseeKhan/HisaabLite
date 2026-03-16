package com.hisaablite.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class UrlService {
    
    /**
     * Get base URL from current request (dynamic)
     */
    public String getCurrentBaseUrl() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String url = request.getRequestURL().toString();
            String path = request.getServletPath();
            return url.replace(path, "");
        }
        // Fallback for non-web contexts (like scheduled tasks)
        return "https://hisaablite.com";
    }
    
    /**
     * Build URL with path
     */
    public String buildUrl(String path) {
        return getCurrentBaseUrl() + path;
    }
    
    /**
     * Get verification URL
     */
    public String getVerificationUrl(String token) {
        return buildUrl("/verify?token=" + token);
    }
    
    /**
     * Get ticket URL for customers
     */
    public String getTicketUrl(String ticketNumber) {
        return buildUrl("/support/ticket/" + ticketNumber);
    }
    
    /**
   
     */
    public String getAdminTicketUrl(String ticketNumber) {
        return buildUrl("/admin/support/ticket/" + ticketNumber);
    }
    
    /**
     * Get reset password URL
     */
    public String getResetPasswordUrl(String token) {
        return buildUrl("/reset-password?token=" + token);
    }
    
    /**
    
     */
    public String getSupportEmail() {
        // You can either get this from properties or return a default
        return "support@hisaablite.com";
    }
    
    /**
  
     */
    public String getPendingApprovalsUrl() {
        return buildUrl("/admin/users/pending-approvals");
    }
    
    /**
   
     */
    public String getDashboardUrl() {
        return buildUrl("/dashboard");
    }
    
    /**
   
     */
    public String getLoginUrl() {
        return buildUrl("/login");
    }
}