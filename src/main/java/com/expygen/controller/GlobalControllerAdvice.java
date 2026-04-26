package com.expygen.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.SubscriptionAccessService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalControllerAdvice {

    private final UserRepository userRepository;
    private final SubscriptionAccessService subscriptionAccessService;
    
    // Admin details from YAML
    @Value("${admin.email:waseemk.aws@gmail.com}")
    private String adminEmail;
    
    @Value("${admin.phone:${app.company.phone:+91 1234567890}}")
    private String adminPhone;
    
    @Value("${admin.name:${app.company.name:Expygen}}")
    private String adminName;
    
    // Contact details from YAML with fallback
    @Value("${app.contact.whatsapp:${app.company.phone:+91 1234567890}}")
    private String whatsappNumber;
    
    @Value("${app.contact.supportEmail:waseemk.aws@gmail.com}")
    private String supportEmail;
    
    @Value("${app.contact.supportPhone:${app.company.phone:+91 1234567890}}")
    private String supportPhone;
    
    // Make all values available in every view
    @ModelAttribute("adminEmail")
    public String getAdminEmail() {
        return adminEmail;
    }
    
    @ModelAttribute("adminPhone")
    public String getAdminPhone() {
        return adminPhone;
    }
    
    @ModelAttribute("adminName")
    public String getAdminName() {
        return adminName;
    }
    
    @ModelAttribute("whatsappNumber")
    public String getWhatsappNumber() {
        return whatsappNumber;
    }
    
    @ModelAttribute("supportEmail")
    public String getSupportEmail() {
        return supportEmail;
    }
    
    @ModelAttribute("supportPhone")
    public String getSupportPhone() {
        return supportPhone;
    }
    
    // Optional: Formatted phone for display
    @ModelAttribute("formattedAdminPhone")
    public String getFormattedAdminPhone() {
        if (adminPhone != null && adminPhone.length() == 10) {
            return "+91 " + adminPhone.substring(0, 5) + " " + adminPhone.substring(5);
        }
        return adminPhone;
    }
    
    @ModelAttribute("formattedSupportPhone")
    public String getFormattedSupportPhone() {
        return supportPhone;
    }

    @ModelAttribute("planType")
    public String getWorkspacePlanType(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return user != null ? subscriptionAccessService.getPlanName(user.getShop()) : null;
    }

    @ModelAttribute("whatsappAvailable")
    public boolean isWhatsappAvailable(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return user != null && subscriptionAccessService.canUseWhatsAppIntegration(user.getShop());
    }

    @ModelAttribute("insightsAvailable")
    public boolean isInsightsAvailable(Authentication authentication) {
        User user = getAuthenticatedUser(authentication);
        return user != null && subscriptionAccessService.canAccessInsights(user.getShop());
    }

    private User getAuthenticatedUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName()).orElse(null);
    }
}
