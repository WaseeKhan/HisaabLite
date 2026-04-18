package com.expygen.controller;

import com.expygen.entity.Shop;
import com.expygen.entity.User;
import com.expygen.repository.UserRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.WorkspaceAccessService;
import com.expygen.service.WorkspaceAccessState;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class WorkspaceAccessController {

    private final UserRepository userRepository;
    private final WorkspaceAccessService workspaceAccessService;
    private final PlanLimitService planLimitService;

    @GetMapping("/workspace-status")
    public String workspaceStatus(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName()).orElseThrow();
        WorkspaceAccessState accessState = workspaceAccessService.getAccessState(user);

        if (accessState == WorkspaceAccessState.ACTIVE) {
            return "redirect:/dashboard";
        }

        Shop shop = user.getShop();
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("plan", shop != null && shop.getPlanType() != null ? shop.getPlanType().name() : "FREE");
        model.addAttribute("accessState", accessState.name());
        model.addAttribute("daysRemaining", shop != null ? planLimitService.getDaysRemaining(shop) : null);

        return "subscription-required";
    }
}
