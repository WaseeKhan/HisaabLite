package com.expygen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AppConfig {
    
    // ==================== APPLICATION INFO ====================
    @Value("${app.name:EXPYGEN}")
    private String appName;

    @Value("${app.tagline:Simplify Pharmacy}")
    private String appTagline;

    @Value("${app.short-code:RXA}")
    private String appShortCode;
    
    @Value("${app.version:2.2.0}")
    private String appVersion;
    
    @Value("${app.base-url:}")
    private String baseUrl;
    
    @Value("${app.env:development}")
    private String environment;
    
    // ==================== EMAIL CONFIGURATION ====================
    @Value("${app.email.from:waseemk.aws@gmail.com}")
    private String fromEmail;
    
    @Value("${app.contact.supportEmail:waseemk.aws@gmail.com}")
    private String supportEmail;

    @Value("${app.email.admin:waseemk.aws@gmail.com}")
    private String adminEmail;

    @Value("${app.email.reply-to:waseemk.aws@gmail.com}")
    private String replyToEmail;

    @Value("${app.payment.receiver-name:Mohd. Waseem Akram}")
    private String paymentReceiverName;

    @Value("${app.payment.provider:PhonePe}")
    private String paymentProvider;

    @Value("${app.payment.qr-image-path:/images/phonepe-qr.jpeg}")
    private String paymentQrImagePath;

    @Value("${app.payment.automation-enabled:false}")
    private boolean paymentAutomationEnabled;

    @Value("${app.payment.automation-token:}")
    private String paymentAutomationToken;

    @Value("${app.payment.gateway-name:Manual QR}")
    private String paymentGatewayName;
    
    // ==================== COMPANY DETAILS ====================
    @Value("${app.company.name:Expygen}")
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

    @Value("${app.subscription.renewal-reminder-days:30}")
    private int subscriptionRenewalReminderDays;

    @Value("${app.subscription.grace-period-days:7}")
    private int subscriptionGracePeriodDays;

    @Value("${app.subscription.automated-reminders-enabled:false}")
    private boolean automatedSubscriptionRemindersEnabled;
    
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
    public String getPaymentReceiverName() { return paymentReceiverName; }
    public String getPaymentProvider() { return paymentProvider; }
    public String getPaymentQrImagePath() { return paymentQrImagePath; }
    public boolean isPaymentAutomationEnabled() { return paymentAutomationEnabled; }
    public String getPaymentAutomationToken() { return paymentAutomationToken; }
    public String getPaymentGatewayName() { return paymentGatewayName; }
    
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
    public int getSubscriptionRenewalReminderDays() { return subscriptionRenewalReminderDays; }
    public int getSubscriptionGracePeriodDays() { return subscriptionGracePeriodDays; }
    public boolean isAutomatedSubscriptionRemindersEnabled() { return automatedSubscriptionRemindersEnabled; }
    
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
