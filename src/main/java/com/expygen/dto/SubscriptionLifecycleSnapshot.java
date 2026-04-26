package com.expygen.dto;

import java.time.LocalDateTime;

import com.expygen.entity.UpgradeRequestStatus;
import com.expygen.service.SubscriptionLifecycleStatus;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubscriptionLifecycleSnapshot {
    SubscriptionLifecycleStatus status;
    String statusLabel;
    String tone;
    String helperText;
    boolean workspaceAccessible;
    boolean freePlan;
    boolean paidPlan;
    boolean renewalWindowOpen;
    boolean gracePeriodActive;
    Long daysRemaining;
    Long graceDaysRemaining;
    Integer renewalReminderDays;
    Integer gracePeriodDays;
    LocalDateTime subscriptionStartDate;
    LocalDateTime subscriptionEndDate;
    boolean commercialRequestInProgress;
    boolean renewalRequestInProgress;
    UpgradeRequestStatus pendingRequestStatus;
}
