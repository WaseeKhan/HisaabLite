package com.hisaablite.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {
    
    // ==================== APPLICATION INFO ====================
    @Value("${app.name:RxArogya}")
    private String appName;

    @Value("${app.tagline:Simplify Pharmacy}")
    private String appTagline;

    @Value("${app.short-code:RXA}")
    private String appShortCode;
    
    @Value("${app.version:2.1.3}")
    private String appVersion;
    
    @Value("${app.base-url:}")
    private String baseUrl;
    
    @Value("${app.env:development}")
    private String environment;
    
    // ==================== EMAIL CONFIGURATION ====================
    @Value("${app.email.from:waseemk.aws@gmail.com}")
    private String fromEmail;
    
    @Value("${app.contact.supportEmail:${admin.email:admin@rxarogya.com}}")
    private String supportEmail;

    @Value("${app.email.admin:admin@rxarogya.com}")
    private String adminEmail;

    @Value("${app.email.reply-to:no-reply@rxarogya.com}")
    private String replyToEmail;
    
    // ==================== COMPANY DETAILS ====================
    @Value("${app.company.name:RxArogya}")
    private String companyName;
    
    @Value("${app.company.address:India}")
    private String companyAddress;
    
    @Value("${app.company.phone:+91 1234567890}")
    private String companyPhone;
    
    // ==================== FEATURE FLAGS ====================
    @Value("${app.features.whatsapp:true}")
    private boolean whatsappEnabled;
    
    @Value("${app.features.email-verification:true}")
    private boolean emailVerificationRequired;
    
    @Value("${app.features.admin-approval:true}")
    private boolean adminApprovalRequired;
    
    // ==================== SECURITY ====================
    @Value("${app.security.verification-token-expiry-hours:24}")
    private int verificationTokenExpiryHours;
    
    @Value("${app.security.reset-token-expiry-minutes:15}")
    private int resetTokenExpiryMinutes;
    
    // ==================== PAGINATION ====================
    @Value("${app.pagination.default-page-size:20}")
    private int defaultPageSize;
    
    @Value("${app.pagination.max-page-size:100}")
    private int maxPageSize;
    
    // ==================== GETTERS ====================
    
    // App Info
    public String getAppName() { return appName; }
    public String getAppTagline() { return appTagline; }
    public String getAppShortCode() { return appShortCode; }
    public String getAppVersion() { return appVersion; }
    public String getBaseUrl() { return baseUrl; }
    public String getEnvironment() { return environment; }
    
    // Email
    public String getFromEmail() { return fromEmail; }
    public String getSupportEmail() { return supportEmail; }
    public String getAdminEmail() { return adminEmail; }
    public String getReplyToEmail() { return replyToEmail; }
    
    // Company
    public String getCompanyName() { return companyName; }
    public String getCompanyAddress() { return companyAddress; }
    public String getCompanyPhone() { return companyPhone; }
    
    // Features
    public boolean isWhatsappEnabled() { return whatsappEnabled; }
    public boolean isEmailVerificationRequired() { return emailVerificationRequired; }
    public boolean isAdminApprovalRequired() { return adminApprovalRequired; }
    
    // Security
    public int getVerificationTokenExpiryHours() { return verificationTokenExpiryHours; }
    public int getResetTokenExpiryMinutes() { return resetTokenExpiryMinutes; }
    
    // Pagination
    public int getDefaultPageSize() { return defaultPageSize; }
    public int getMaxPageSize() { return maxPageSize; }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Build full URL with path
     */
    public String buildUrl(String path) {
        return (baseUrl == null ? "" : baseUrl) + path;
    }
    
    /**
     * Get verification URL
     */
    public String getVerificationUrl(String token) {
        return buildUrl("/verify?token=" + token);
    }
    
    /**
     * Get reset password URL
     */
    public String getResetPasswordUrl(String token) {
        return buildUrl("/reset-password?token=" + token);
    }
    
    /**
     * Get ticket URL for customers
     */
    public String getTicketUrl(String ticketNumber) {
        return buildUrl("/support/ticket/" + ticketNumber);
    }
    
    /**
     * Get admin ticket URL
     */
    public String getAdminTicketUrl(String ticketNumber) {
        return buildUrl("/admin/support/ticket/" + ticketNumber);
    }
    
    /**
     * Get pending approvals URL
     */
    public String getPendingApprovalsUrl() {
        return buildUrl("/admin/users/pending-approvals");
    }
    
    /**
     * Get dashboard URL
     */
    public String getDashboardUrl() {
        return buildUrl("/dashboard");
    }
    
    /**
     * Get login URL
     */
    public String getLoginUrl() {
        return buildUrl("/login");
    }
    
    /**
     * Get registration URL
     */
    public String getRegisterUrl() {
        return buildUrl("/register");
    }
    
    /**
     * Check if current environment is production
     */
    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment) || "prod".equalsIgnoreCase(environment);
    }
    
    /**
     * Check if current environment is development
     */
    public boolean isDevelopment() {
        return "development".equalsIgnoreCase(environment) || "dev".equalsIgnoreCase(environment);
    }

    public String getAdminPortalName() {
        return appName + " Admin";
    }

    public String getSupportTeamName() {
        return appName + " Support Team";
    }
}
