package com.expygen.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.expygen.dto.SubscriptionEntitlementItem;
import com.expygen.entity.Shop;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionEntitlementAuditService {

    private final PlanLimitService planLimitService;
    private final SubscriptionAccessService subscriptionAccessService;
    private final SubscriptionLifecycleService subscriptionLifecycleService;

    public List<SubscriptionEntitlementItem> buildAudit(Shop shop) {
        var lifecycle = subscriptionLifecycleService.buildSnapshot(shop);
        var plan = planLimitService.getCurrentPlan(shop);

        return List.of(
                item("workspace", "Workspace Access",
                        lifecycle.isWorkspaceAccessible() ? "Enabled" : "Blocked",
                        lifecycle.isWorkspaceAccessible() ? "healthy" : "critical",
                        lifecycle.getHelperText()),
                item("products", "Product Capacity",
                        plan.getMaxProducts() == -1 ? "Unlimited" : String.valueOf(plan.getMaxProducts()),
                        plan.getMaxProducts() == -1 ? "healthy" : "neutral",
                        "Product creation and master maintenance follow this plan ceiling."),
                item("users", "User Seats",
                        plan.getMaxUsers() == -1 ? "Unlimited" : String.valueOf(plan.getMaxUsers()),
                        plan.getMaxUsers() == -1 ? "healthy" : "neutral",
                        "Owner and staff access are enforced from this plan."),
                item("insights", "Insights Workspace",
                        subscriptionAccessService.canAccessInsights(shop) ? "Enabled" : "Locked",
                        subscriptionAccessService.canAccessInsights(shop) ? "healthy" : "warning",
                        subscriptionAccessService.canAccessInsights(shop)
                                ? "Business intelligence, reports, and import desks are available."
                                : subscriptionAccessService.getInsightsUpgradeMessage(shop)),
                item("whatsapp", "WhatsApp Automation",
                        subscriptionAccessService.canUseWhatsAppIntegration(shop) ? "Enabled" : "Locked",
                        subscriptionAccessService.canUseWhatsAppIntegration(shop) ? "healthy" : "warning",
                        subscriptionAccessService.canUseWhatsAppIntegration(shop)
                                ? "Invoice sharing and WhatsApp operations are available."
                                : subscriptionAccessService.getWhatsAppUpgradeMessage(shop))
        );
    }

    private SubscriptionEntitlementItem item(String code, String label, String availability, String tone, String helperText) {
        return SubscriptionEntitlementItem.builder()
                .code(code)
                .label(label)
                .availability(availability)
                .tone(tone)
                .helperText(helperText)
                .build();
    }
}
