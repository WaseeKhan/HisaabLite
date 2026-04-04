package com.hisaablite.security;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hisaablite.admin.service.AuditService;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthAuditHelper {

    private final AuditService auditService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public void logLoginSuccess(Authentication authentication, HttpServletRequest request) {
        if (authentication == null) {
            return;
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        String role = user != null ? user.getRole().name() : resolveRole(authentication);

        auditService.logAction(
                username,
                role,
                user != null ? user.getShop() : null,
                "LOGIN_SUCCESS",
                "AUTH",
                user != null ? user.getId() : null,
                "SUCCESS",
                null,
                null,
                toJson(detailsForRequest(request, "Login successful", null)));
    }

    public void logLoginFailure(String username, String reason, HttpServletRequest request) {
        User user = username != null ? userRepository.findByUsername(username).orElse(null) : null;
        String resolvedUsername = (username == null || username.isBlank()) ? "UNKNOWN" : username;
        String role = user != null ? user.getRole().name() : "UNKNOWN";

        auditService.logAction(
                resolvedUsername,
                role,
                user != null ? user.getShop() : null,
                "LOGIN_FAILED",
                "AUTH",
                user != null ? user.getId() : null,
                "FAILED",
                null,
                null,
                toJson(detailsForRequest(request, "Login failed", reason)));
    }

    public void logLogout(Authentication authentication, HttpServletRequest request, String actionName) {
        if (authentication == null) {
            return;
        }

        String username = authentication.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        String role = user != null ? user.getRole().name() : resolveRole(authentication);

        auditService.logAction(
                username,
                role,
                user != null ? user.getShop() : null,
                actionName,
                "AUTH",
                user != null ? user.getId() : null,
                "SUCCESS",
                null,
                null,
                toJson(detailsForRequest(request, "User logged out", null)));
    }

    private String resolveRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .findFirst()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .orElse(Role.OWNER.name());
    }

    private Map<String, Object> detailsForRequest(HttpServletRequest request, String message, String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("message", message);

        if (reason != null && !reason.isBlank()) {
            details.put("reason", reason);
        }

        if (request != null) {
            details.put("path", request.getRequestURI());
            details.put("method", request.getMethod());
            details.put("sessionId", request.getSession(false) != null ? request.getSession(false).getId() : null);
            details.put("userAgent", request.getHeader("User-Agent"));
        }

        return details;
    }

    private String toJson(Map<String, Object> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            log.debug("Could not serialize auth audit details", e);
            return details.toString();
        }
    }
}
