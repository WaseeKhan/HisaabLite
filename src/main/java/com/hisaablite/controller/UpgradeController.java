package com.hisaablite.controller;

import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/upgrade")
@RequiredArgsConstructor
@Slf4j
public class UpgradeController {

    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @GetMapping
    public String upgradePage(Authentication authentication, Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop shop = user.getShop();

        String planType = shop.getPlanType() != null ? shop.getPlanType().name() : "FREE";

        model.addAttribute("shop", shop);
        model.addAttribute("planType", planType);
        model.addAttribute("user", user);
        model.addAttribute("currentPage", "upgrade");
         model.addAttribute("role", user.getRole().name()); 

        // Get plan limits for comparison
        model.addAttribute("currentProductCount", planLimitService.getCurrentProductCount(shop));
        model.addAttribute("currentStaffCount", userRepository.countByShop(shop));
        
        return "upgrade";
    }

    @GetMapping("/request/{planName}")
    public String upgradeRequest(@PathVariable String planName,
            Authentication authentication,
            Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Shop shop = user.getShop();

        String currentPlan = shop.getPlanType() != null ? shop.getPlanType().name() : "FREE";

        // Don't allow upgrade to same plan
        if (currentPlan.equalsIgnoreCase(planName)) {
            return "redirect:/upgrade?error=already_on_this_plan";
        }
        model.addAttribute("currentPage", "upgrade");
        model.addAttribute("selectedPlan", planName);
        model.addAttribute("currentPlan", currentPlan);
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);

        return "upgrade-request";
    }
}