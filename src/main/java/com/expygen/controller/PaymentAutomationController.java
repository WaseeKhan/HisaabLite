package com.expygen.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.expygen.config.AppConfig;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.UpgradeRequestService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class PaymentAutomationController {

    private final UpgradeRequestService upgradeRequestService;
    private final UserRepository userRepository;
    private final AppConfig appConfig;

    @PostMapping("/internal/payments/upgrade-requests/{id}/confirm")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> confirmPayment(@PathVariable Long id,
                                                              @RequestParam String paymentReference,
                                                              @RequestParam(required = false) String gatewayTransactionId,
                                                              @RequestParam(required = false) Double amount,
                                                              @RequestHeader(value = "X-Expygen-Payment-Token", required = false) String token,
                                                              Authentication authentication) {
        if (!appConfig.isPaymentAutomationEnabled()) {
            throw new RuntimeException("Payment automation is disabled.");
        }
        if (appConfig.getPaymentAutomationToken() == null
                || appConfig.getPaymentAutomationToken().isBlank()
                || !appConfig.getPaymentAutomationToken().equals(token)) {
            throw new RuntimeException("Invalid payment automation token.");
        }

        User actor = null;
        if (authentication != null && authentication.isAuthenticated()) {
            actor = userRepository.findByUsername(authentication.getName()).orElse(null);
        }

        var request = upgradeRequestService.confirmAutomatedPayment(id, paymentReference, gatewayTransactionId, amount, actor);
        return ResponseEntity.ok(Map.of(
                "requestId", request.getId(),
                "status", request.getStatus().name(),
                "paymentReference", request.getPaymentReference() != null ? request.getPaymentReference() : "",
                "gateway", appConfig.getPaymentGatewayName()));
    }
}
