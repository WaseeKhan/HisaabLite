package com.hisaablite.service;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.hisaablite.dto.RegisterRequest;
import com.hisaablite.entity.EmailVerificationToken;
import com.hisaablite.entity.Role;
import com.hisaablite.entity.Shop;
import com.hisaablite.entity.PlanType; 
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

    @Transactional
    public void registerShop(RegisterRequest request, String appUrl) {

        // Duplicate checks
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Email already registered");
        }

        if (shopRepository.existsByPanNumber(request.getPanNumber())) {
            throw new DuplicateResourceException("PAN already registered.");
        }
        
        if (shopRepository.existsByPanNumber(request.getPhone())) {
            throw new DuplicateResourceException("Phone Number already registered.");
        }
        

      
        PlanType selectedPlan = request.getPlanType();
        log.info("Selected plan: {}", selectedPlan);

        // Save shop (WITH PLAN SELECTION)
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

        shop = shopRepository.save(shop);
        log.info("Shop created: {} with plan: {}", shop.getName(), shop.getPlanType());

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

        userRepository.save(owner);

        // Create verification token
        String token = UUID.randomUUID().toString();

        EmailVerificationToken verificationToken = new EmailVerificationToken();
        verificationToken.setToken(token);
        verificationToken.setUser(owner);
        verificationToken.setTokenType(TokenType.EMAIL_VERIFICATION);
        verificationToken.setExpiryDate(LocalDateTime.now().plusHours(24));

        tokenRepository.save(verificationToken);

        // Send verification email
        String verifyLink = appUrl + "/verify?token=" + token;
        emailService.sendVerificationEmail(owner.getUsername(), verifyLink);
        
        log.info("Registration completed - Shop: {}, Plan: {}, Owner: {}", 
                shop.getName(), shop.getPlanType(), owner.getUsername());
    }
}