package com.expygen.service;

import com.expygen.admin.service.AuditService;
import com.expygen.entity.*;
import com.expygen.repository.SubscriptionPlanRepository;
import com.expygen.repository.ShopRepository;
import com.expygen.repository.UpgradeRequestRepository;
import com.expygen.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UpgradeRequestService {

    private static final List<UpgradeRequestStatus> OPEN_REQUEST_STATUSES = List.of(
            UpgradeRequestStatus.REQUESTED,
            UpgradeRequestStatus.CONTACTED,
            UpgradeRequestStatus.PAYMENT_RECEIVED);

    private final UpgradeRequestRepository upgradeRequestRepository;
    private final SubscriptionPlanRepository subscriptionPlanRepository;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final SubscriptionReceiptStorageService subscriptionReceiptStorageService;
    private final SubscriptionLedgerService subscriptionLedgerService;

    public List<UpgradeRequest> getRecentRequestsForShop(Shop shop) {
        return upgradeRequestRepository.findTop10ByShopOrderByCreatedAtDesc(shop);
    }

    public Page<UpgradeRequest> getRequests(UpgradeRequestStatus status, Pageable pageable) {
        if (status == null) {
            return upgradeRequestRepository.findAllByOrderByCreatedAtDesc(pageable);
        }
        return upgradeRequestRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    public UpgradeRequest getRequest(Long requestId) {
        return upgradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Upgrade request not found"));
    }

    @Transactional
    public UpgradeRequest createRequest(User owner,
                                        PlanType requestedPlan,
                                        String paymentPreference,
                                        String paymentReference,
                                        String note,
                                        MultipartFile receiptFile) {
        Shop shop = owner.getShop();
        PlanType currentPlan = shop.getPlanType() != null ? shop.getPlanType() : PlanType.FREE;

        if (requestedPlan == currentPlan && currentPlan == PlanType.FREE) {
            throw new RuntimeException("FREE workspace does not need a renewal request. Choose BASIC or PRO.");
        }

        SubscriptionPlan selectedPlan = subscriptionPlanRepository.findByPlanName(requestedPlan.name())
                .orElseThrow(() -> new RuntimeException("Requested plan not configured: " + requestedPlan));

        upgradeRequestRepository.findFirstByShopAndStatusInOrderByCreatedAtDesc(
                        shop,
                        OPEN_REQUEST_STATUSES)
                .ifPresent(existing -> {
                    throw new RuntimeException("An upgrade request is already in progress for this workspace.");
                });

        UpgradeRequest request = UpgradeRequest.builder()
                .shop(shop)
                .requestedBy(owner)
                .currentPlan(currentPlan)
                .requestedPlan(requestedPlan)
                .requestedAnnualPrice(selectedPlan.getEffectiveAnnualPrice())
                .requestedDurationInDays(selectedPlan.getDurationInDays())
                .paymentPreference(paymentPreference == null ? null : paymentPreference.trim())
                .paymentReference(paymentReference == null ? null : paymentReference.trim())
                .note(note == null ? null : note.trim())
                .status(UpgradeRequestStatus.REQUESTED)
                .createdAt(LocalDateTime.now())
                .statusUpdatedAt(LocalDateTime.now())
                .build();

        UpgradeRequest savedRequest = upgradeRequestRepository.save(request);
        attachReceiptIfPresent(savedRequest, receiptFile);
        emailService.sendUpgradeRequestCreatedEmail(savedRequest);

        auditService.logAction(
                owner.getUsername(),
                owner.getRole().name(),
                shop,
                "UPGRADE_REQUEST_CREATED",
                "UPGRADE_REQUEST",
                savedRequest.getId(),
                "SUCCESS",
                currentPlan.name(),
                requestedPlan.name(),
                requestedPlan == currentPlan
                        ? "Yearly renewal requested by owner"
                        : "Plan upgrade requested by owner");

        subscriptionLedgerService.recordEntry(
                shop,
                savedRequest,
                requestedPlan,
                "REQUEST_SUBMITTED",
                savedRequest.getRequestedAnnualPrice(),
                savedRequest.getRequestedDurationInDays(),
                null,
                null,
                savedRequest.getPaymentReference(),
                "UPGRADE_REQUEST#" + savedRequest.getId(),
                savedRequest.getNote(),
                owner.getUsername());

        log.info("Upgrade request {} created for shop {} from {} to {}",
                savedRequest.getId(), shop.getName(), currentPlan, requestedPlan);

        return savedRequest;
    }

    @Transactional
    public UpgradeRequest updateStatus(Long requestId, UpgradeRequestStatus status, String adminNote, String paymentReference, User admin) {
        UpgradeRequest request = upgradeRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Upgrade request not found"));

        LocalDateTime now = LocalDateTime.now();
        request.setStatus(status);
        request.setAdminNote(adminNote);
        request.setStatusUpdatedAt(now);
        if (paymentReference != null && !paymentReference.isBlank()) {
            request.setPaymentReference(paymentReference.trim());
        }
        stampCommercialTimeline(request, status, now);

        if (status == UpgradeRequestStatus.PAYMENT_RECEIVED) {
            recordPaymentReceivedLedgerEntry(request, admin);
        }

        if (status == UpgradeRequestStatus.ACTIVATED) {
            activatePlan(request, admin, adminNote);
        }

        UpgradeRequest saved = upgradeRequestRepository.save(request);

        auditService.logAction(
                admin.getUsername(),
                admin.getRole().name(),
                request.getShop(),
                "UPGRADE_REQUEST_STATUS_UPDATED",
                "UPGRADE_REQUEST",
                saved.getId(),
                status.name(),
                null,
                status.name(),
                "Upgrade request updated by admin");

        return saved;
    }

    @Transactional
    public UpgradeRequest confirmAutomatedPayment(Long requestId,
                                                  String paymentReference,
                                                  String gatewayTransactionId,
                                                  Double amount,
                                                  User actor) {
        UpgradeRequest request = getRequest(requestId);
        LocalDateTime now = LocalDateTime.now();

        request.setStatus(UpgradeRequestStatus.PAYMENT_RECEIVED);
        request.setStatusUpdatedAt(now);
        request.setPaymentReceivedAt(now);
        request.setAutomatedPaymentConfirmedAt(now);
        request.setPaymentGateway("AUTOMATION");
        if (gatewayTransactionId != null && !gatewayTransactionId.isBlank()) {
            request.setGatewayTransactionId(gatewayTransactionId.trim());
        }
        if (paymentReference != null && !paymentReference.isBlank()) {
            request.setPaymentReference(paymentReference.trim());
        }
        if (request.getContactedAt() == null) {
            request.setContactedAt(now);
        }

        UpgradeRequest saved = upgradeRequestRepository.save(request);

        subscriptionLedgerService.recordEntry(
                request.getShop(),
                saved,
                request.getRequestedPlan(),
                "AUTOMATED_PAYMENT_CONFIRMED",
                amount != null ? amount : request.getRequestedAnnualPrice(),
                request.getRequestedDurationInDays(),
                null,
                null,
                saved.getPaymentReference(),
                saved.getGatewayTransactionId(),
                "Payment automation marked this request as paid.",
                actor != null ? actor.getUsername() : "PAYMENT_AUTOMATION");

        return saved;
    }

    private void activatePlan(UpgradeRequest request, User admin, String adminNote) {
        SubscriptionPlan newPlan = subscriptionPlanRepository.findByPlanName(request.getRequestedPlan().name())
                .orElseThrow(() -> new RuntimeException("Requested plan not configured: " + request.getRequestedPlan()));

        Shop shop = request.getShop();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime previousEndDate = shop.getSubscriptionEndDate();
        LocalDateTime activationBase = resolveActivationBaseDate(previousEndDate, now);
        LocalDateTime effectiveUntil = resolveExpiry(request, newPlan, activationBase);

        shop.setPlanType(request.getRequestedPlan());
        shop.setStaffLimit(newPlan.getMaxUsers() == -1 ? shop.getStaffLimit() : newPlan.getMaxUsers());
        if (shop.getSubscriptionStartDate() == null || previousEndDate == null || !previousEndDate.isAfter(now)) {
            shop.setSubscriptionStartDate(now);
        }
        shop.setSubscriptionEndDate(effectiveUntil);
        shopRepository.save(shop);

        List<User> users = userRepository.findByShop(shop);
        for (User user : users) {
            user.setCurrentPlan(request.getRequestedPlan());
            user.setApproved(true);
            user.setSubscriptionStartDate(shop.getSubscriptionStartDate());
            user.setSubscriptionEndDate(shop.getSubscriptionEndDate());
        }
        userRepository.saveAll(users);

        request.setActivatedAt(now);
        request.setActivatedBy(admin);
        request.setAdminNote(adminNote);

        subscriptionLedgerService.recordEntry(
                shop,
                request,
                request.getRequestedPlan(),
                request.getCurrentPlan() == request.getRequestedPlan() ? "RENEWAL_ACTIVATED" : "PLAN_ACTIVATED",
                request.getRequestedAnnualPrice(),
                request.getRequestedDurationInDays(),
                activationBase,
                effectiveUntil,
                request.getPaymentReference(),
                "UPGRADE_REQUEST#" + request.getId(),
                adminNote,
                admin.getUsername());

        User owner = request.getRequestedBy();
        emailService.sendPlanActivatedEmail(owner, newPlan, request);
    }

    private LocalDateTime resolveExpiry(UpgradeRequest request, SubscriptionPlan plan, LocalDateTime baseDate) {
        Integer durationInDays = request.getRequestedDurationInDays() != null
                ? request.getRequestedDurationInDays()
                : plan.getDurationInDays();
        if (durationInDays == null || durationInDays <= 0) {
            return null;
        }
        return baseDate.plusDays(durationInDays);
    }

    private LocalDateTime resolveActivationBaseDate(LocalDateTime existingEndDate, LocalDateTime now) {
        if (existingEndDate != null && existingEndDate.isAfter(now)) {
            return existingEndDate;
        }
        return now;
    }

    private void stampCommercialTimeline(UpgradeRequest request, UpgradeRequestStatus status, LocalDateTime now) {
        switch (status) {
            case CONTACTED -> {
                if (request.getContactedAt() == null) {
                    request.setContactedAt(now);
                }
            }
            case PAYMENT_RECEIVED -> {
                if (request.getContactedAt() == null) {
                    request.setContactedAt(now);
                }
                if (request.getPaymentReceivedAt() == null) {
                    request.setPaymentReceivedAt(now);
                }
            }
            case ACTIVATED -> {
                if (request.getContactedAt() == null) {
                    request.setContactedAt(now);
                }
                if (request.getPaymentReceivedAt() == null) {
                    request.setPaymentReceivedAt(now);
                }
            }
            case REJECTED -> {
                if (request.getRejectedAt() == null) {
                    request.setRejectedAt(now);
                }
            }
            case CANCELLED -> {
                if (request.getCancelledAt() == null) {
                    request.setCancelledAt(now);
                }
            }
            default -> {
            }
        }
    }

    private void attachReceiptIfPresent(UpgradeRequest request, MultipartFile receiptFile) {
        if (receiptFile == null || receiptFile.isEmpty()) {
            return;
        }
        SubscriptionReceiptStorageService.StoredReceipt storedReceipt = subscriptionReceiptStorageService.store(receiptFile, request.getId());
        request.setReceiptOriginalFilename(storedReceipt.getOriginalFilename());
        request.setReceiptStoredFilename(storedReceipt.getStoredFilename());
        request.setReceiptContentType(storedReceipt.getContentType());
        request.setReceiptUploadedAt(storedReceipt.getUploadedAt());
        upgradeRequestRepository.save(request);
    }

    private void recordPaymentReceivedLedgerEntry(UpgradeRequest request, User admin) {
        subscriptionLedgerService.recordEntry(
                request.getShop(),
                request,
                request.getRequestedPlan(),
                "PAYMENT_RECEIVED",
                request.getRequestedAnnualPrice(),
                request.getRequestedDurationInDays(),
                null,
                null,
                request.getPaymentReference(),
                "UPGRADE_REQUEST#" + request.getId(),
                "Payment marked as received by admin.",
                admin.getUsername());
    }
}
