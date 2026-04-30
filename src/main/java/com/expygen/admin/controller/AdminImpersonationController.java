package com.expygen.admin.controller;

import com.expygen.admin.repository.AdminShopRepository;
import com.expygen.admin.repository.AdminUserRepository;
import com.expygen.admin.service.AuditService;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.security.CustomUserDetailsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
@Slf4j
@RequestMapping
public class AdminImpersonationController {

    public static final String IMPERSONATOR_USERNAME = "adminImpersonatorUsername";
    public static final String IMPERSONATED_OWNER_USERNAME = "adminImpersonatedOwnerUsername";

    private final AdminShopRepository adminShopRepository;
    private final AdminUserRepository adminUserRepository;
    private final CustomUserDetailsService customUserDetailsService;
    private final AuditService auditService;

    @PostMapping("/admin/shops/{id}/impersonate")
    public String startImpersonation(@PathVariable Long id,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        try {
            String adminUsername = SecurityContextHolder.getContext().getAuthentication().getName();
            Shop shop = adminShopRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Shop not found."));
            User owner = adminUserRepository.findFirstByShopAndRoleOrderByIdAsc(shop, Role.OWNER)
                    .orElseThrow(() -> new RuntimeException("Owner account not found for this shop."));

            var userDetails = customUserDetailsService.loadUserByUsername(owner.getUsername());
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    userDetails.getPassword(),
                    userDetails.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(true);
            session.setAttribute(IMPERSONATOR_USERNAME, adminUsername);
            session.setAttribute(IMPERSONATED_OWNER_USERNAME, owner.getUsername());
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            auditService.logAction(
                    adminUsername,
                    "ADMIN",
                    shop,
                    "ADMIN_IMPERSONATION_STARTED",
                    "User",
                    owner.getId(),
                    "SUCCESS",
                    null,
                    owner.getUsername(),
                    "Admin started owner impersonation");

            return "redirect:/dashboard";
        } catch (Exception e) {
            log.error("Error starting impersonation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Could not start impersonation: " + e.getMessage());
            return "redirect:/admin/shops/" + id;
        }
    }

    @PostMapping("/app/admin-impersonation/stop")
    public String stopImpersonation(HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            redirectAttributes.addFlashAttribute("error", "No impersonation session found.");
            return "redirect:/admin/dashboard";
        }

        String adminUsername = (String) session.getAttribute(IMPERSONATOR_USERNAME);
        String impersonatedOwnerUsername = (String) session.getAttribute(IMPERSONATED_OWNER_USERNAME);

        if (adminUsername == null || adminUsername.isBlank()) {
            redirectAttributes.addFlashAttribute("error", "No impersonation session found.");
            return "redirect:/admin/dashboard";
        }

        try {
            var adminDetails = customUserDetailsService.loadUserByUsername(adminUsername);
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    adminDetails,
                    adminDetails.getPassword(),
                    adminDetails.getAuthorities());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
            session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

            User admin = adminUserRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new RuntimeException("Admin account not found."));
            User impersonatedOwner = impersonatedOwnerUsername != null
                    ? adminUserRepository.findByUsername(impersonatedOwnerUsername).orElse(null)
                    : null;

            auditService.logAction(
                    adminUsername,
                    "ADMIN",
                    impersonatedOwner != null ? impersonatedOwner.getShop() : admin.getShop(),
                    "ADMIN_IMPERSONATION_STOPPED",
                    "User",
                    impersonatedOwner != null ? impersonatedOwner.getId() : admin.getId(),
                    "SUCCESS",
                    impersonatedOwnerUsername,
                    adminUsername,
                    "Admin ended owner impersonation");

            session.removeAttribute(IMPERSONATOR_USERNAME);
            session.removeAttribute(IMPERSONATED_OWNER_USERNAME);
            redirectAttributes.addFlashAttribute("success", "Returned to admin session.");
            return "redirect:/admin/dashboard";
        } catch (Exception e) {
            log.error("Error stopping impersonation: {}", e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Could not stop impersonation: " + e.getMessage());
            return "redirect:/dashboard";
        }
    }
}
