package com.expygen.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.expygen.security.AuthAuditHelper;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.WorkspaceAccessService;
import com.expygen.service.WorkspaceAccessState;

import java.io.IOException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CustomAuthFailureHandler implements AuthenticationFailureHandler {

    private final AuthAuditHelper authAuditHelper;
    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception)
            throws IOException {

        String message;

        if (exception instanceof DisabledException) {
            message = resolveDisabledMessage(request.getParameter("username"));
        } else {
            message = "bad"; // keyword for wrong credentials
        }

        authAuditHelper.logLoginFailure(request.getParameter("username"), exception.getMessage(), request);

        message = java.net.URLEncoder.encode(message, "UTF-8");
        response.sendRedirect("/login?error=" + message);
    }

    private String resolveDisabledMessage(String username) {
        if (username == null || username.isBlank()) {
            return "This account is not available right now. Please contact support.";
        }

        return userRepository.findByUsername(username)
                .map(this::toDisabledMessage)
                .orElse("This account is not available right now. Please contact support.");
    }

    private String toDisabledMessage(User user) {
        WorkspaceAccessState state = workspaceAccessService.getAccessState(user);
        return switch (state) {
            case EMAIL_VERIFICATION_REQUIRED -> "Please verify your email before logging in.";
            case APPROVAL_PENDING -> "Your account is verified and waiting for approval. Please contact Expygen support if needed.";
            case SHOP_INACTIVE -> "Your shop workspace is inactive. Please contact Expygen support.";
            case SUBSCRIPTION_EXPIRED -> "Your subscription has expired. Please log in again or contact Expygen support.";
            case ACTIVE -> "This account is not available right now. Please contact support.";
        };
    }
}
