package com.expygen.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.expygen.dto.SubscriptionUsageView;
import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.SubscriptionAccessService;
import com.expygen.service.SubscriptionUsageService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final UserRepository userRepository;
    private final SubscriptionUsageService subscriptionUsageService;
    private final SubscriptionAccessService subscriptionAccessService;

    @GetMapping
    public String subscriptionPage(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop shop = user.getShop();

        SubscriptionUsageView usageView = subscriptionUsageService.buildUsageView(shop);
        model.addAttribute("subscriptionView", usageView);
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("planType", subscriptionAccessService.getPlanName(shop));
        model.addAttribute("currentPage", "subscription");
        model.addAttribute("currentPageName", "subscription");

        return "subscription";
    }
}
