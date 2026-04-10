package com.expygen.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.expygen.entity.SubscriptionPlan;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionUsageView {
    private String currentPlanName;
    private String currentPlanDescription;
    private boolean subscriptionActive;
    private boolean trialPlan;
    private Long daysRemaining;
    private LocalDateTime subscriptionStartDate;
    private LocalDateTime subscriptionEndDate;
    private Double currentPlanPrice;
    private String currentPlanFeatures;
    private UsageMetric productUsage;
    private UsageMetric userUsage;
    private List<UsageMetric> metrics;
    private List<SubscriptionPlan> availablePlans;
}
