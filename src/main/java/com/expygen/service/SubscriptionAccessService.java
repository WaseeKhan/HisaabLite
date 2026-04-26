package com.expygen.service;

import org.springframework.stereotype.Service;

import com.expygen.config.AppConfig;
import com.expygen.entity.PlanType;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionAccessService {

    private final PlanLimitService planLimitService;
    private final AppConfig appConfig;

    public SubscriptionPlan getCurrentPlan(Shop shop) {
        return planLimitService.getCurrentPlan(shop);
    }

    public String getPlanName(Shop shop) {
        return getCurrentPlan(shop).getPlanName();
    }

    public PlanType getPlanTypeOrFree(Shop shop) {
        if (shop == null || shop.getPlanType() == null) {
            return PlanType.FREE;
        }
        return shop.getPlanType();
    }

    public boolean isFreePlan(Shop shop) {
        return getPlanTypeOrFree(shop) == PlanType.FREE;
    }

    public boolean isUnlimitedUsers(Shop shop) {
        return planLimitService.getUserLimit(shop) == -1;
    }

    public boolean isUnlimitedProducts(Shop shop) {
        return planLimitService.getProductLimit(shop) == -1;
    }

    public boolean canUseWhatsAppIntegration(Shop shop) {
        if (!appConfig.isWhatsappEnabled()) {
            return false;
        }
        return getPlanTypeOrFree(shop) == PlanType.PRO;
    }

    public boolean canAccessInsights(Shop shop) {
        return getPlanTypeOrFree(shop) == PlanType.PRO;
    }

    public String getWhatsAppUpgradeMessage(Shop shop) {
        if (!appConfig.isWhatsappEnabled()) {
            return "WhatsApp integration is currently disabled by system configuration.";
        }
        if (canUseWhatsAppIntegration(shop)) {
            return null;
        }
        return "WhatsApp integration is available on the PRO plan.";
    }

    public String getInsightsUpgradeMessage(Shop shop) {
        if (canAccessInsights(shop)) {
            return null;
        }
        return "Insights workspace is available on the PRO plan.";
    }
}
