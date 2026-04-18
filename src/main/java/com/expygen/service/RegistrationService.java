package com.expygen.service;

import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expygen.dto.RegisterRequest;
import com.expygen.entity.EmailVerificationToken;
import com.expygen.entity.PlanType;
import com.expygen.entity.Role;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.TokenType;
import com.expygen.entity.User;
import com.expygen.exception.DuplicateResourceException;
import com.expygen.repository.EmailVerificationTokenRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UserRepository;

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
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional(rollbackFor = Exception.class)
    public void registerShop(RegisterRequest request, String appUrl) {
        PlanType registrationPlan = PlanType.FREE;

        log.info("Starting registration for email: {} with auto trial plan: {}", request.getUsername(), registrationPlan);

        try {
            // Duplicate checks
            if (userRepository.existsByUsername(request.getUsername())) {
                throw new DuplicateResourceException("Email already registered");
            }

            if (userRepository.existsByPhone(request.getPhone())) {
                throw new DuplicateResourceException("Phone Number already registered.");
            }

            // Fetch plan details from database using the selected plan type
            SubscriptionPlan selectedPlan = subscriptionPlanRepository.findByPlanName(registrationPlan.name())
                    .orElseThrow(() -> new RuntimeException("Selected registration plan not found in database: " + registrationPlan));

            log.info("Fetched plan from DB: {} ({} days, ₹{}, Max Users: {}, Max Products: {})", 
                     selectedPlan.getPlanName(), 
                     selectedPlan.getDurationInDays(),
                     selectedPlan.getPrice(),
                     selectedPlan.getMaxUsers(),
                     selectedPlan.getMaxProducts());

            // Calculate expiry date based on plan duration
            LocalDateTime expiryDate = null;
            if (selectedPlan.getDurationInDays() != null && selectedPlan.getDurationInDays() > 0) {
                expiryDate = LocalDateTime.now().plusDays(selectedPlan.getDurationInDays());
            }

            // Save shop with subscription details
            Shop shop = Shop.builder()
                    .name(request.getShopName())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .staffLimit(selectedPlan.getMaxUsers() != -1 ? selectedPlan.getMaxUsers() : 5)
                    .planType(registrationPlan)
                    .subscriptionStartDate(LocalDateTime.now())
                    .subscriptionEndDate(expiryDate)
                    .active(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            log.info("Saving shop: {}", shop.getName());
            shop = shopRepository.save(shop);
            log.info("Shop saved with ID: {}", shop.getId());

            // Save owner with subscription details
            User owner = User.builder()
                    .name(request.getOwnerName())
                    .username(request.getUsername())
                    .phone(request.getPhone())
                    .password(passwordEncoder.encode(request.getPassword()))
                    .role(Role.OWNER)
                    .shop(shop)
                    .active(false) // Will be true after email verification
                    .approved(true)
                    .currentPlan(registrationPlan)
                    .subscriptionStartDate(LocalDateTime.now())
                    .subscriptionEndDate(expiryDate)
                    .approvalDate(LocalDateTime.now())
                    .createdAt(LocalDateTime.now())
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

            // Generate verification link
            String verifyLink = appUrl + "/verify?token=" + token;
            
            emailService.sendVerificationEmail(owner, verifyLink, selectedPlan);

            log.info("Verification email sent to: {} (free trial activates after verification + login)",
                    owner.getUsername());

            log.info("Registration completed successfully for shop: {} with plan: {}", 
                     shop.getName(), selectedPlan.getPlanName());

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}
