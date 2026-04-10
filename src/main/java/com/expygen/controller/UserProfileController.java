package com.expygen.controller;

import com.expygen.entity.PlanType;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
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

    @GetMapping
    public String userProfile(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        PlanType planType = user.getShop().getPlanType() != null ? user.getShop().getPlanType() : PlanType.FREE;

        model.addAttribute("shop", user.getShop());
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("planType", planType.name());
        model.addAttribute("currentPageName", "user-profile");

        return "user-profile";
    }
}
