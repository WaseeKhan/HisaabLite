package com.expygen.controller;

import com.expygen.config.AppConfig;
import com.expygen.entity.PlanType;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.User;
import com.expygen.entity.UpgradeRequest;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UserRepository;
import com.expygen.service.PlanLimitService;
import com.expygen.service.SubscriptionLifecycleService;
import com.expygen.service.UpgradeRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/upgrade")
@RequiredArgsConstructor
@Slf4j
public class UpgradeController {

    private final UserRepository userRepository;
    private final PlanLimitService planLimitService;
    private final UpgradeRequestService upgradeRequestService;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AppConfig appConfig;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

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
        model.addAttribute("currentPageName", "upgrade");
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("recentRequests", upgradeRequestService.getRecentRequestsForShop(shop));
        model.addAttribute("subscriptionLifecycle", subscriptionLifecycleService.buildSnapshot(shop));
        model.addAttribute("plansByName", subscriptionPlanRepository.findByActiveTrue().stream()
                .collect(Collectors.toMap(plan -> plan.getPlanName().toUpperCase(), plan -> plan, (first, second) -> first)));

        // Get plan limits for comparison
        model.addAttribute("currentProductCount", planLimitService.getCurrentProductCount(shop));
        model.addAttribute("currentStaffCount", userRepository.countByShop(shop));
        model.addAttribute("productLimit", planLimitService.getProductLimit(shop));
        model.addAttribute("staffLimit", planLimitService.getUserLimit(shop));
        
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

        boolean renewalRequest = currentPlan.equalsIgnoreCase(planName) && !"FREE".equalsIgnoreCase(currentPlan);
        if (currentPlan.equalsIgnoreCase(planName) && !renewalRequest) {
            return "redirect:/upgrade?error=already_on_this_plan";
        }
        SubscriptionPlan selectedPlan = subscriptionPlanRepository.findByPlanName(planName.toUpperCase())
                .orElseThrow(() -> new RuntimeException("Plan not found: " + planName));
        model.addAttribute("currentPage", "upgrade");
        model.addAttribute("selectedPlan", planName);
        model.addAttribute("currentPlan", currentPlan);
        model.addAttribute("selectedPlanDetails", selectedPlan);
        model.addAttribute("shop", shop);
        model.addAttribute("user", user);
        model.addAttribute("role", user.getRole().name());
        model.addAttribute("planType", currentPlan);
        model.addAttribute("currentPageName", "upgrade");
        model.addAttribute("recentRequests", upgradeRequestService.getRecentRequestsForShop(shop));
        model.addAttribute("paymentReceiverName", appConfig.getPaymentReceiverName());
        model.addAttribute("paymentProvider", appConfig.getPaymentProvider());
        model.addAttribute("paymentQrImagePath", appConfig.getPaymentQrImagePath());
        model.addAttribute("paymentGatewayName", appConfig.getPaymentGatewayName());
        model.addAttribute("renewalRequest", renewalRequest);
        model.addAttribute("requestActionLabel", renewalRequest ? "Submit renewal request" : "Submit upgrade request");
        model.addAttribute("requestHeading", renewalRequest ? "We are ready to renew your yearly plan" : "We are ready to process your plan change");

        return "upgrade-request";
    }

    @PostMapping("/request/{planName}")
    public String submitUpgradeRequest(@PathVariable String planName,
                                       @RequestParam(required = false) String paymentPreference,
                                       @RequestParam(required = false) String paymentReference,
                                       @RequestParam(required = false) String note,
                                       @RequestParam(required = false) MultipartFile receiptFile,
                                       Authentication authentication,
                                       Model model) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/login";
        }

        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        try {
            if (paymentReference == null || paymentReference.isBlank()) {
                throw new RuntimeException("Please enter the payment transaction reference / UTR.");
            }

            UpgradeRequest request = upgradeRequestService.createRequest(
                    user,
                    PlanType.valueOf(planName.toUpperCase()),
                    paymentPreference,
                    paymentReference,
                    note,
                    receiptFile);
            return "redirect:/upgrade?requested=" + request.getRequestedPlan().name();
        } catch (Exception e) {
            Shop shop = user.getShop();
            String currentPlan = shop.getPlanType() != null ? shop.getPlanType().name() : "FREE";
            SubscriptionPlan selectedPlan = subscriptionPlanRepository.findByPlanName(planName.toUpperCase())
                    .orElse(null);
            model.addAttribute("currentPage", "upgrade");
            model.addAttribute("selectedPlan", planName.toUpperCase());
            model.addAttribute("currentPlan", currentPlan);
            model.addAttribute("selectedPlanDetails", selectedPlan);
            model.addAttribute("shop", shop);
            model.addAttribute("user", user);
            model.addAttribute("role", user.getRole().name());
            model.addAttribute("planType", currentPlan);
            model.addAttribute("currentPageName", "upgrade");
            model.addAttribute("recentRequests", upgradeRequestService.getRecentRequestsForShop(shop));
            model.addAttribute("paymentReceiverName", appConfig.getPaymentReceiverName());
            model.addAttribute("paymentProvider", appConfig.getPaymentProvider());
            model.addAttribute("paymentQrImagePath", appConfig.getPaymentQrImagePath());
            model.addAttribute("paymentGatewayName", appConfig.getPaymentGatewayName());
            model.addAttribute("renewalRequest", currentPlan.equalsIgnoreCase(planName) && !"FREE".equalsIgnoreCase(currentPlan));
            model.addAttribute("requestActionLabel", currentPlan.equalsIgnoreCase(planName) && !"FREE".equalsIgnoreCase(currentPlan)
                    ? "Submit renewal request"
                    : "Submit upgrade request");
            model.addAttribute("requestHeading", currentPlan.equalsIgnoreCase(planName) && !"FREE".equalsIgnoreCase(currentPlan)
                    ? "We are ready to renew your yearly plan"
                    : "We are ready to process your plan change");
            model.addAttribute("error", e.getMessage());
            return "upgrade-request";
        }
    }
}
