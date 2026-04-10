package com.expygen.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.expygen.dto.SubscriptionUsageView;
import com.expygen.dto.UsageMetric;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.repository.ProductRepository;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionUsageService {

    private final PlanLimitService planLimitService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;

    public SubscriptionUsageView buildUsageView(Shop shop) {
        SubscriptionPlan currentPlan = planLimitService.getCurrentPlan(shop);

        long activeProducts = productRepository.countByShopAndActiveTrue(shop);
        long currentUsers = userRepository.countByShop(shop);

        UsageMetric productUsage = toMetric(
                "products",
                "Medicines",
                activeProducts,
                currentPlan.getMaxProducts(),
                "Active medicines using your current plan slots");

        UsageMetric userUsage = toMetric(
                "users",
                "Users",
                currentUsers,
                currentPlan.getMaxUsers(),
                "Owner and staff accounts under this shop");

        return SubscriptionUsageView.builder()
                .currentPlanName(currentPlan.getPlanName())
                .currentPlanDescription(currentPlan.getDescription())
                .subscriptionActive(planLimitService.isSubscriptionActive(shop))
                .trialPlan(shop.getPlanType() != null && shop.getPlanType().name().equalsIgnoreCase("FREE"))
                .daysRemaining(planLimitService.getDaysRemaining(shop))
                .subscriptionStartDate(shop.getSubscriptionStartDate())
                .subscriptionEndDate(shop.getSubscriptionEndDate())
                .currentPlanPrice(currentPlan.getPrice())
                .currentPlanFeatures(currentPlan.getFeatures())
                .productUsage(productUsage)
                .userUsage(userUsage)
                .metrics(List.of(productUsage, userUsage))
                .availablePlans(subscriptionPlanRepository.findActivePlansOrderedByPrice())
                .build();
    }

    private UsageMetric toMetric(String key, String label, long used, Integer limit, String helperText) {
        boolean unlimited = limit != null && limit == -1;
        long remaining = unlimited ? -1 : Math.max(0, (limit != null ? limit : 0) - used);
        int percentage = unlimited || limit == null || limit <= 0 ? 0 : (int) Math.min(100, (used * 100) / limit);

        String tone = percentage >= 100 ? "critical" : percentage >= 80 ? "warning" : "healthy";
        if (unlimited) {
            tone = "unlimited";
        }

        return UsageMetric.builder()
                .key(key)
                .label(label)
                .used(used)
                .limit(limit)
                .remaining(remaining)
                .percentage(percentage)
                .unlimited(unlimited)
                .helperText(helperText)
                .tone(tone)
                .build();
    }
}
