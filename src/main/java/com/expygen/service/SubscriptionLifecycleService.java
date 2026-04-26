package com.expygen.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.expygen.admin.service.AuditService;
import com.expygen.config.AppConfig;
import com.expygen.dto.SubscriptionLifecycleSnapshot;
import com.expygen.entity.PlanType;
import com.expygen.entity.Shop;
import com.expygen.entity.SubscriptionPlan;
import com.expygen.entity.UpgradeRequest;
import com.expygen.entity.UpgradeRequestStatus;
import com.expygen.entity.User;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.UpgradeRequestRepository;
import com.expygen.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {

    private static final List<UpgradeRequestStatus> OPEN_REQUEST_STATUSES = List.of(
            UpgradeRequestStatus.REQUESTED,
            UpgradeRequestStatus.CONTACTED,
            UpgradeRequestStatus.PAYMENT_RECEIVED);

    private final AppConfig appConfig;
    private final UpgradeRequestRepository upgradeRequestRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final AuditService auditService;
    private final SubscriptionLedgerService subscriptionLedgerService;

    public SubscriptionLifecycleSnapshot buildSnapshot(Shop shop) {
        if (shop == null) {
            return SubscriptionLifecycleSnapshot.builder()
                    .status(SubscriptionLifecycleStatus.EXPIRED)
                    .statusLabel("Unavailable")
                    .tone("critical")
                    .helperText("Workspace details could not be loaded.")
                    .workspaceAccessible(false)
                    .freePlan(false)
                    .paidPlan(false)
                    .renewalReminderDays(appConfig.getSubscriptionRenewalReminderDays())
                    .gracePeriodDays(appConfig.getSubscriptionGracePeriodDays())
                    .build();
        }

        PlanType planType = shop.getPlanType() != null ? shop.getPlanType() : PlanType.FREE;
        boolean freePlan = planType == PlanType.FREE;
        boolean paidPlan = !freePlan;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = shop.getSubscriptionEndDate();
        Integer reminderDays = appConfig.getSubscriptionRenewalReminderDays();
        Integer graceDays = appConfig.getSubscriptionGracePeriodDays();

        Optional<UpgradeRequest> pendingRequest = upgradeRequestRepository
                .findFirstByShopAndStatusInOrderByCreatedAtDesc(shop, OPEN_REQUEST_STATUSES);
        boolean commercialRequestInProgress = pendingRequest.isPresent();
        boolean renewalRequestInProgress = pendingRequest
                .map(request -> request.getRequestedPlan() == planType && paidPlan)
                .orElse(false);

        if (freePlan && endDate == null) {
            return SubscriptionLifecycleSnapshot.builder()
                    .status(SubscriptionLifecycleStatus.FREE)
                    .statusLabel("Free plan")
                    .tone("neutral")
                    .helperText(commercialRequestInProgress
                            ? "A commercial request is already in progress for this workspace."
                            : "Free workspaces stay active until you choose a paid yearly plan.")
                    .workspaceAccessible(true)
                    .freePlan(true)
                    .paidPlan(false)
                    .renewalWindowOpen(false)
                    .gracePeriodActive(false)
                    .daysRemaining(null)
                    .graceDaysRemaining(null)
                    .renewalReminderDays(reminderDays)
                    .gracePeriodDays(graceDays)
                    .subscriptionStartDate(shop.getSubscriptionStartDate())
                    .subscriptionEndDate(endDate)
                    .commercialRequestInProgress(commercialRequestInProgress)
                    .renewalRequestInProgress(false)
                    .pendingRequestStatus(pendingRequest.map(UpgradeRequest::getStatus).orElse(null))
                    .build();
        }

        if (endDate == null) {
            return SubscriptionLifecycleSnapshot.builder()
                    .status(SubscriptionLifecycleStatus.ACTIVE)
                    .statusLabel("Active")
                    .tone("healthy")
                    .helperText(commercialRequestInProgress
                            ? "A commercial request is in progress while the workspace stays active."
                            : "This workspace is active without a scheduled renewal date.")
                    .workspaceAccessible(true)
                    .freePlan(freePlan)
                    .paidPlan(paidPlan)
                    .renewalWindowOpen(false)
                    .gracePeriodActive(false)
                    .daysRemaining(null)
                    .graceDaysRemaining(null)
                    .renewalReminderDays(reminderDays)
                    .gracePeriodDays(graceDays)
                    .subscriptionStartDate(shop.getSubscriptionStartDate())
                    .subscriptionEndDate(endDate)
                    .commercialRequestInProgress(commercialRequestInProgress)
                    .renewalRequestInProgress(renewalRequestInProgress)
                    .pendingRequestStatus(pendingRequest.map(UpgradeRequest::getStatus).orElse(null))
                    .build();
        }

        if (!now.isAfter(endDate)) {
            long daysRemaining = Math.max(0L, Duration.between(now, endDate).toDays());
            boolean renewalDue = paidPlan && daysRemaining <= reminderDays;

            return SubscriptionLifecycleSnapshot.builder()
                    .status(renewalDue ? SubscriptionLifecycleStatus.RENEWAL_DUE : SubscriptionLifecycleStatus.ACTIVE)
                    .statusLabel(renewalDue ? "Renewal due" : "Active")
                    .tone(renewalDue ? "warning" : "healthy")
                    .helperText(resolveActiveHelperText(daysRemaining, commercialRequestInProgress, renewalRequestInProgress))
                    .workspaceAccessible(true)
                    .freePlan(freePlan)
                    .paidPlan(paidPlan)
                    .renewalWindowOpen(renewalDue)
                    .gracePeriodActive(false)
                    .daysRemaining(daysRemaining)
                    .graceDaysRemaining(null)
                    .renewalReminderDays(reminderDays)
                    .gracePeriodDays(graceDays)
                    .subscriptionStartDate(shop.getSubscriptionStartDate())
                    .subscriptionEndDate(endDate)
                    .commercialRequestInProgress(commercialRequestInProgress)
                    .renewalRequestInProgress(renewalRequestInProgress)
                    .pendingRequestStatus(pendingRequest.map(UpgradeRequest::getStatus).orElse(null))
                    .build();
        }

        long expiredDays = Math.max(0L, Duration.between(endDate, now).toDays());
        boolean gracePeriodActive = paidPlan && expiredDays < graceDays;
        Long graceDaysRemaining = gracePeriodActive ? Math.max(0L, graceDays - expiredDays) : 0L;

        return SubscriptionLifecycleSnapshot.builder()
                .status(gracePeriodActive ? SubscriptionLifecycleStatus.GRACE_PERIOD : SubscriptionLifecycleStatus.EXPIRED)
                .statusLabel(gracePeriodActive ? "Grace period" : "Expired")
                .tone(gracePeriodActive ? "warning" : "critical")
                .helperText(resolveExpiredHelperText(gracePeriodActive, graceDaysRemaining, commercialRequestInProgress))
                .workspaceAccessible(gracePeriodActive)
                .freePlan(freePlan)
                .paidPlan(paidPlan)
                .renewalWindowOpen(false)
                .gracePeriodActive(gracePeriodActive)
                .daysRemaining(0L)
                .graceDaysRemaining(graceDaysRemaining)
                .renewalReminderDays(reminderDays)
                .gracePeriodDays(graceDays)
                .subscriptionStartDate(shop.getSubscriptionStartDate())
                .subscriptionEndDate(endDate)
                .commercialRequestInProgress(commercialRequestInProgress)
                .renewalRequestInProgress(renewalRequestInProgress)
                .pendingRequestStatus(pendingRequest.map(UpgradeRequest::getStatus).orElse(null))
                .build();
    }

    public boolean canAccessWorkspace(Shop shop) {
        return buildSnapshot(shop).isWorkspaceAccessible();
    }

    @Transactional
    public Shop renewCurrentPlan(Long shopId, User admin, String adminNote) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Shop not found"));

        PlanType planType = shop.getPlanType() != null ? shop.getPlanType() : PlanType.FREE;
        if (planType == PlanType.FREE) {
            throw new RuntimeException("FREE workspace does not need yearly renewal. Use upgrade flow instead.");
        }

        SubscriptionPlan plan = subscriptionPlanRepository.findByPlanName(planType.name())
                .orElseThrow(() -> new RuntimeException("Plan configuration missing for " + planType));

        int durationInDays = plan.getDurationInDays() != null && plan.getDurationInDays() > 0
                ? plan.getDurationInDays()
                : 365;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime baseDate = shop.getSubscriptionEndDate() != null && shop.getSubscriptionEndDate().isAfter(now)
                ? shop.getSubscriptionEndDate()
                : now;

        if (shop.getSubscriptionStartDate() == null
                || shop.getSubscriptionEndDate() == null
                || !shop.getSubscriptionEndDate().isAfter(now)) {
            shop.setSubscriptionStartDate(now);
        }
        shop.setSubscriptionEndDate(baseDate.plusDays(durationInDays));
        shop.setStaffLimit(plan.getMaxUsers() == -1 ? shop.getStaffLimit() : plan.getMaxUsers());
        shopRepository.save(shop);

        List<User> users = userRepository.findByShop(shop);
        for (User user : users) {
            user.setCurrentPlan(planType);
            user.setApproved(true);
            user.setSubscriptionStartDate(shop.getSubscriptionStartDate());
            user.setSubscriptionEndDate(shop.getSubscriptionEndDate());
        }
        userRepository.saveAll(users);

        auditService.logAction(
                admin.getUsername(),
                admin.getRole().name(),
                shop,
                "SUBSCRIPTION_RENEWED",
                "SHOP",
                shop.getId(),
                "SUCCESS",
                planType.name(),
                planType.name(),
                adminNote != null && !adminNote.isBlank()
                        ? adminNote.trim()
                        : "Current yearly plan renewed for " + durationInDays + " days");

        subscriptionLedgerService.recordEntry(
                shop,
                null,
                planType,
                "DIRECT_ADMIN_RENEWAL",
                plan.getEffectiveAnnualPrice(),
                durationInDays,
                baseDate,
                shop.getSubscriptionEndDate(),
                null,
                "SHOP#" + shop.getId(),
                adminNote,
                admin.getUsername());

        return shop;
    }

    private String resolveActiveHelperText(long daysRemaining,
                                           boolean commercialRequestInProgress,
                                           boolean renewalRequestInProgress) {
        if (renewalRequestInProgress) {
            return "Renewal request is already in progress while this plan stays active.";
        }
        if (commercialRequestInProgress) {
            return "A commercial request is already in progress for this workspace.";
        }
        if (daysRemaining <= 0) {
            return "Your current subscription window ends today. Complete renewal before the day closes to avoid service interruption.";
        }
        if (daysRemaining == 1) {
            return "This workspace reaches renewal day tomorrow. Renew now to avoid access risk.";
        }
        return "Renew before the due date to avoid service interruption.";
    }

    private String resolveExpiredHelperText(boolean gracePeriodActive,
                                            Long graceDaysRemaining,
                                            boolean commercialRequestInProgress) {
        if (gracePeriodActive) {
            long safeGraceDaysRemaining = graceDaysRemaining != null ? graceDaysRemaining : 0L;
            if (commercialRequestInProgress) {
                return safeGraceDaysRemaining <= 1
                        ? "A commercial request is already in progress. Grace access ends today, so admin activation is now urgent."
                        : "A commercial request is already in progress. Complete renewal before grace access ends in "
                                + safeGraceDaysRemaining + " days.";
            }
            if (safeGraceDaysRemaining <= 1) {
                return "Workspace access is still available today, but grace access ends once today is over.";
            }
            return "Workspace access is still available for " + safeGraceDaysRemaining
                    + " more days while you complete yearly renewal.";
        }
        if (commercialRequestInProgress) {
            return "A commercial request exists, but grace access has ended. Admin activation is now required.";
        }
        return "Grace access has ended. Renew or upgrade to reactivate this workspace.";
    }
}
