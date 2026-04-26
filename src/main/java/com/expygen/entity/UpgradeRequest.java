package com.expygen.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "upgrade_requests")
public class UpgradeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType currentPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlanType requestedPlan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private UpgradeRequestStatus status = UpgradeRequestStatus.REQUESTED;

    private String paymentPreference;

    private Double requestedAnnualPrice;

    private Integer requestedDurationInDays;

    private String paymentReference;

    private String receiptOriginalFilename;

    private String receiptStoredFilename;

    private String receiptContentType;

    private LocalDateTime receiptUploadedAt;

    private String gatewayTransactionId;

    private String paymentGateway;

    private LocalDateTime automatedPaymentConfirmedAt;

    @Column(length = 1000)
    private String note;

    @Column(length = 1000)
    private String adminNote;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private LocalDateTime statusUpdatedAt;

    private LocalDateTime contactedAt;

    private LocalDateTime paymentReceivedAt;

    private LocalDateTime activatedAt;

    private LocalDateTime rejectedAt;

    private LocalDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activated_by")
    private User activatedBy;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
