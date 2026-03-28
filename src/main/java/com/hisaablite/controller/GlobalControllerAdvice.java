package com.hisaablite.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {
    
    // Admin details from YAML
    @Value("${admin.email}")
    private String adminEmail;
    
    @Value("${admin.phone}")
    private String adminPhone;
    
    @Value("${admin.name}")
    private String adminName;
    
    // Contact details from YAML with fallback
    @Value("${app.contact.whatsapp:${admin.phone}}")
    private String whatsappNumber;
    
    @Value("${app.contact.supportEmail}")
    private String supportEmail;
    
    @Value("${app.contact.supportPhone}")
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
}