package com.expygen.controller;

import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.SubscriptionAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequiredArgsConstructor
@RequestMapping("/user-profile")
public class UserProfileController {

    private final UserRepository userRepository;
    private final SubscriptionAccessService subscriptionAccessService;

    @GetMapping
    public String userProfile(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();

        model.addAttribute("shop", user.getShop());
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("planType", subscriptionAccessService.getPlanName(user.getShop()));
        model.addAttribute("currentPageName", "user-profile");

        return "user-profile";
    }
}
