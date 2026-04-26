package com.expygen.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.expygen.entity.EmailVerificationToken;
import com.expygen.entity.PlanType;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.User;
import com.expygen.repository.EmailVerificationTokenRepository;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.EmailService;
import com.expygen.service.RegistrationService;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class VerificationController {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final EmailService emailService;
    private final RegistrationService registrationService;

    @GetMapping("/verify")
    public String verifyAccount(@RequestParam String token, Model model, HttpServletResponse response) {

        Optional<EmailVerificationToken> tokenOpt =
                Optional.ofNullable(tokenRepository.findByToken(token));

        if (tokenOpt.isEmpty() ||
                tokenOpt.get().getExpiryDate().isBefore(LocalDateTime.now())) {

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", "This verification link is invalid or has expired.");
            return "error/404";
        }

        EmailVerificationToken verificationToken = tokenOpt.get();

        User user = verificationToken.getUser();
        user.setActive(true);

        userRepository.save(user);

        SubscriptionPlan plan = subscriptionPlanRepository.findByPlanName(
                        user.getCurrentPlan() != null ? user.getCurrentPlan().name() : PlanType.FREE.name())
                .orElseThrow(() -> new RuntimeException("Current plan not configured in database"));

        emailService.sendWelcomeEmail(user, plan);

        tokenRepository.delete(verificationToken);

        model.addAttribute("message", "Account verified successfully. Please login to start your free trial.");
        return "redirect:/login?verified=active";
    }

    @PostMapping("/verify/resend")
    public String resendVerification(@RequestParam String username, Model model) {
        try {
            registrationService.resendVerificationEmail(username);
            return "redirect:/login?verificationResent";
        } catch (Exception e) {
            model.addAttribute("resendError", e.getMessage());
            return "login";
        }
    }
}
