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
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.entity.TokenType;
import com.hisaablite.entity.User;
import com.hisaablite.exception.DuplicateResourceException;
import com.hisaablite.repository.EmailVerificationTokenRepository;
import com.hisaablite.repository.ShopRepository;
import com.hisaablite.repository.SubscriptionPlanRepository;
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
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    @Transactional(rollbackFor = Exception.class)
    public void registerShop(RegisterRequest request, String appUrl) {

        log.info("Starting registration for email: {} with plan: {}", request.getUsername(), request.getPlanType());

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

            // Fetch plan details from database using the selected plan type
            SubscriptionPlan selectedPlan = subscriptionPlanRepository.findByPlanName(request.getPlanType().name())
                    .orElseThrow(() -> new RuntimeException("Selected plan not found in database: " + request.getPlanType()));

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
                    .panNumber(request.getPanNumber())
                    .address(request.getAddress())
                    .city(request.getCity())
                    .state(request.getState())
                    .staffLimit(selectedPlan.getMaxUsers() != -1 ? selectedPlan.getMaxUsers() : 5)
                    .planType(request.getPlanType())
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
                    .approved(request.getPlanType() == PlanType.FREE) // Auto-approve only FREE plans
                    .currentPlan(request.getPlanType())
                    .subscriptionStartDate(LocalDateTime.now())
                    .subscriptionEndDate(expiryDate)
                    .approvalDate(request.getPlanType() == PlanType.FREE ? LocalDateTime.now() : null)
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
            
            // Send appropriate email based on plan type
            if (request.getPlanType() == PlanType.FREE) {
                // For FREE plan - send welcome email (auto-approved)
                emailService.sendWelcomeEmail(owner, selectedPlan);
                log.info("Welcome email sent to: {} (FREE plan - auto-approved)", owner.getUsername());
            } else {
                // For paid plans - send verification email
                emailService.sendVerificationEmail(owner, verifyLink, selectedPlan);
                log.info("Verification email sent to: {} ({} plan - pending approval)", 
                         owner.getUsername(), selectedPlan.getPlanName());
                
                // Notify admin about pending approval (optional)
                emailService.notifyAdminAboutPendingApproval(owner, selectedPlan);
            }

            log.info("Registration completed successfully for shop: {} with plan: {}", 
                     shop.getName(), selectedPlan.getPlanName());

        } catch (Exception e) {
            log.error("Registration failed: {}", e.getMessage(), e);
            throw e;
        }
    }
}