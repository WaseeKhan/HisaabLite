package com.hisaablite.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hisaablite.dto.RegisterRequest;
import com.hisaablite.entity.EmailVerificationToken;
import com.hisaablite.entity.PlanType;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.TokenType;
import com.hisaablite.entity.User;
import com.hisaablite.exception.DuplicateResourceException;
import com.hisaablite.repository.EmailVerificationTokenRepository;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationTokenRepository tokenRepository;
    private final EmailService emailService;
    // private final EvolutionApiService evolutionApiService; // Comment out for now

    @Transactional(rollbackFor = Exception.class)
    public void registerShop(RegisterRequest request, String appUrl) {

        log.info("Starting registration for email: {}", request.getUsername());

        try {
            // Duplicate checks
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new DuplicateResourceException("Email already registered");
            }

            if (shopRepository.existsByPanNumber(request.getPanNumber())) {
                throw new DuplicateResourceException("PAN already registered.");
            }

            if (userRepository.existsByPhone(request.getPhone())) {
                throw new DuplicateResourceException("Phone Number already registered.");
            }

            PlanType selectedPlan = request.getPlanType();
            log.info("Selected plan: {}", selectedPlan);

            // Save shop
            Shop shop = Shop.builder()
                    .name(request.getShopName())
                    .panNumber(request.getPanNumber())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .staffLimit(5)
                    .planType(selectedPlan)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("Saving shop: {}", shop.getName());
            shop = shopRepository.save(shop);
            log.info("Shop saved with ID: {}", shop.getId());

            // Save owner
            User owner = User.builder()
                    .name(request.getOwnerName())
                    .username(request.getUsername())
                    .phone(request.getPhone())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.OWNER)
                    .shop(shop)
                    .active(false)
                    .approved(false)
                    .build();

            log.info("Saving owner: {}", owner.getUsername());
            userRepository.save(owner);
            log.info("Owner saved");

            // Create verification token
            String token = UUID.randomUUID().toString();
            EmailVerificationToken verificationToken = new EmailVerificationToken();
            verificationToken.setToken(token);
            verificationToken.setUser(owner);
            verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION);
            verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));

            tokenRepository.save(verificationToken);
            log.info("Verification token saved");

            // Send verification email
            String verifyLink = appUrl + "/verify?token=" + token;
            emailService.sendVerificationEmail(owner.getUsername(), verifyLink);
            log.info("Verification email sent to: {}", owner.getUsername());

            log.info("Registration completed successfully for shop: {}", shop.getName());

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage(), e);
            throw e; // Re-throw to trigger rollback
        }
    }
}