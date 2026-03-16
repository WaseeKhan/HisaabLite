package com.hisaablite.controller;

import com.hisaablite.entity.Shop;
import com.hisaablite.entity.User;
import com.hisaablite.entity.SubscriptionPlan;
import com.hisaablite.repository.UserRepository;
import com.hisaablite.service.PlanLimitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/upgrade")
@RequiredArgsConstructor
public class UpgradeController {

    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;

    @GetMapping
    public String upgradePage(Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow();
        
        Shop shop = user.getShop();
        
        model.addAttribute("currentPlan", planLimitService.getCurrentPlan(shop));
        model.addAttribute("availablePlans", planLimitService.getAvailablePlans(shop));
        model.addAttribute("usageStats", planLimitService.getUsageStats(shop));
        
        return "upgrade";
    }
}