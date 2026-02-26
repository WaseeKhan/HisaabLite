package com.hisaablite.service;

import com.hisaablite.dto.RegisterRequest;
import com.hisaablite.entity.*;
import com.hisaablite.exception.DuplicateResourceException;
import com.hisaablite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void registerShop(RegisterRequest request) {

        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Email already registered");
        }

        if (shopRepository.existsByPanNumber(request.getPanNumber())) {
            throw new DuplicateResourceException("PAN already registered");
        }

        // 1 Save shop
        Shop shop = Shop.builder()
                .name(request.getShopName())
                .panNumber(request.getPanNumber())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .staffLimit(5)
                .subscriptionPlan(SubscriptionPlan.FREE)
                .active(true)
                .build();

        shop = shopRepository.save(shop);

        // 2 Save owner
        User owner = User.builder()
                .name(request.getOwnerName())
                .username(request.getUsername())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.OWNER)
                .shop(shop)
                .active(true)
                .build();

        userRepository.save(owner);
    }
}