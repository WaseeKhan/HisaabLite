package com.hisaablite.controller;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.hisaablite.entity.EmailVerificationToken;
import com.hisaablite.entity.User;
import com.hisaablite.repository.EmailVerificationTokenRepository;
import com.hisaablite.repository.UserRepository;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class VerificationController {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;

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

        tokenRepository.delete(verificationToken);

        model.addAttribute("message", "Account verified successfully. Please login.");
       
        return "redirect:/login?verified";
    }
}
