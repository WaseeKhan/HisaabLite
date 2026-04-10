package com.expygen.service;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class UrlService {

    @Value("${app.base-url:}")
    private String configuredBaseUrl;

    @Value("${app.contact.supportEmail:${admin.email:admin@expygen.com}}")
    private String supportEmail;
    
    /**
     * Get base URL from current request (dynamic)
     */
    public String getCurrentBaseUrl() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            String forwardedProto = firstHeaderValue(request.getHeader("X-Forwarded-Proto"));
            String forwardedHost = firstHeaderValue(request.getHeader("X-Forwarded-Host"));
            String forwardedPort = firstHeaderValue(request.getHeader("X-Forwarded-Port"));

            String scheme = hasText(forwardedProto) ? forwardedProto : request.getScheme();
            String host = hasText(forwardedHost) ? forwardedHost : request.getServerName();
            int port = hasText(forwardedPort) ? Integer.parseInt(forwardedPort) : request.getServerPort();

            if (host.contains(":")) {
                return scheme + "://" + host;
            }

            boolean defaultPort = ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);

            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        }

        if (hasText(configuredBaseUrl)) {
            return configuredBaseUrl.endsWith("/") ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                    : configuredBaseUrl;
        }

        return "http://localhost:8080";
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

    public String getRenewUrl() {
        return buildUrl("/renew");
    }

    public String getPlansUrl() {
        return buildUrl("/plans");
    }

    public String getUpgradeUrl() {
        return buildUrl("/upgrade");
    }
    
    /**
    
     */
    public String getSupportEmail() {
        return supportEmail;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String firstHeaderValue(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.split(",")[0].trim();
    }
}
