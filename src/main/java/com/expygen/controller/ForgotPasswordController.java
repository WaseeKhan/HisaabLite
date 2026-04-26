package com.expygen.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.expygen.entity.PasswordResetToken;
import com.expygen.entity.User;
import com.expygen.repository.PasswordResetTokenRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.EmailService;
import com.expygen.service.UrlService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class ForgotPasswordController {

    private static final int RESET_TOKEN_EXPIRY_MINUTES = 15;
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final String GENERIC_RESET_REQUEST_MESSAGE =
            "If an account exists for that email, a reset link has been sent.";
    private static final String INVALID_RESET_LINK_MESSAGE =
            "This password reset link is invalid or has expired.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final UrlService urlService;

    // // Constructor Injection (ALL DEPENDENCIES)
    // public ForgotPasswordController(UserRepository userRepository,
    // PasswordResetTokenRepository passwordResetTokenRepository,
    // PasswordEncoder passwordEncoder) {
    // this.userRepository = userRepository;
    // this.passwordResetTokenRepository = passwordResetTokenRepository;
    // this.passwordEncoder = passwordEncoder;
    // }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String processForgotPassword(@RequestParam String username,
            Model model) {

        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isEmpty()) {
            model.addAttribute("error", "Please enter your email address.");
            return "forgot-password";
        }

        Optional<User> userOpt = userRepository.findByUsername(normalizedUsername);

        if (userOpt.isEmpty()) {
            model.addAttribute("message", GENERIC_RESET_REQUEST_MESSAGE);
            return "forgot-password";
        }

        User user = userOpt.get();

        // DELETE OLD TOKEN IF EXISTS
        passwordResetTokenRepository.deleteByUser(user);

        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setToken(token);
        resetToken.setUser(user);
        resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRY_MINUTES));

        passwordResetTokenRepository.save(resetToken);

        String resetLink = urlService.getResetPasswordUrl(token);

        emailService.sendResetEmail(user.getUsername(), resetLink);

        // model.addAttribute("message", "Reset link generated. Check console.");
        model.addAttribute("message", GENERIC_RESET_REQUEST_MESSAGE);
        return "forgot-password";
    }

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam(required = false) String token, Model model, HttpServletResponse response) {

        if (token == null || token.isBlank()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", INVALID_RESET_LINK_MESSAGE);
            return "error/404";
        }

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);

        if (tokenOpt.isEmpty() ||
                tokenOpt.get().getExpiryDate().isBefore(LocalDateTime.now())) {
            tokenOpt.ifPresent(passwordResetTokenRepository::delete);

            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", INVALID_RESET_LINK_MESSAGE);
            return "error/404";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            Model model,
            HttpServletResponse response) {

        Optional<PasswordResetToken> tokenOpt = passwordResetTokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", INVALID_RESET_LINK_MESSAGE);
            return "error/404";
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            passwordResetTokenRepository.delete(resetToken);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            model.addAttribute("message", INVALID_RESET_LINK_MESSAGE);
            return "error/404";
        }

        if (password == null || password.trim().length() < MIN_PASSWORD_LENGTH) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "reset-password";
        }

        if (!password.equals(confirmPassword)) {
            model.addAttribute("token", token);
            model.addAttribute("error", "Passwords do not match.");
            return "reset-password";
        }

        User user = resetToken.getUser();
        if (passwordEncoder.matches(password, user.getPassword())) {
            model.addAttribute("token", token);
            model.addAttribute("error", "New password must be different from the current password.");
            return "reset-password";
        }

        user.setPassword(passwordEncoder.encode(password.trim()));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);

        return "redirect:/login?resetSuccess";
    }
}
