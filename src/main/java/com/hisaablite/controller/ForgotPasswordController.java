package com.hisaablite.controller;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.hisaablite.entity.PasswordResetToken;
import com.hisaablite.entity.User;
import com.hisaablite.repository.PasswordResetTokenRepository;
import com.hisaablite.repository.UserRepository;

@Controller
public class ForgotPasswordController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // Constructor Injection (ALL DEPENDENCIES)
    public ForgotPasswordController(UserRepository userRepository,
                                    PasswordResetTokenRepository passwordResetTokenRepository,
                                    PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
public String processForgotPassword(@RequestParam String username,
                                    Model model) {

    Optional<User> userOpt = userRepository.findByUsername(username);

    if (userOpt.isEmpty()) {
        model.addAttribute("error", "No account found.");
        return "forgot-password";
    }

    User user = userOpt.get();

    // DELETE OLD TOKEN IF EXISTS
    passwordResetTokenRepository.deleteByUser(user);

    String token = UUID.randomUUID().toString();

    PasswordResetToken resetToken = new PasswordResetToken();
    resetToken.setToken(token);
    resetToken.setUser(user);
    resetToken.setExpiryDate(LocalDateTime.now().plusMinutes(15));

    passwordResetTokenRepository.save(resetToken);

    System.out.println("Reset Link: http://localhost:8080/reset-password?token=" + token);

    model.addAttribute("message", "Reset link generated. Check console.");
    return "forgot-password";
}

    @GetMapping("/reset-password")
    public String showResetPassword(@RequestParam String token, Model model) {

        Optional<PasswordResetToken> tokenOpt =
                passwordResetTokenRepository.findByToken(token);

        if (tokenOpt.isEmpty() ||
            tokenOpt.get().getExpiryDate().isBefore(LocalDateTime.now())) {

            model.addAttribute("error", "Invalid or expired token.");
            return "error";
        }

        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String processResetPassword(@RequestParam String token,
                                       @RequestParam String password,
                                       Model model) {

        Optional<PasswordResetToken> tokenOpt =
                passwordResetTokenRepository.findByToken(token);

        if (tokenOpt.isEmpty()) {
            model.addAttribute("error", "Invalid token.");
            return "error";
        }

        PasswordResetToken resetToken = tokenOpt.get();

        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            model.addAttribute("error", "Token expired.");
            return "error";
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(password));
        userRepository.save(user);

        passwordResetTokenRepository.delete(resetToken);

        return "redirect:/login?resetSuccess";
    }
}