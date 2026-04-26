package com.expygen.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String gstNumber;

    private String address;

    private String city;

    private String state;

    private String pincode;

    private Integer staffLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type")
    @Builder.Default
    private PlanType planType = PlanType.FREE;

    private String upiId;

    @Column(name = "logo_original_filename")
    private String logoOriginalFilename;

    @Column(name = "logo_stored_filename")
    private String logoStoredFilename;

    @Column(name = "logo_content_type")
    private String logoContentType;

    @Column(name = "logo_uploaded_at")
    private LocalDateTime logoUploadedAt;

    @Column(name = "seal_original_filename")
    private String sealOriginalFilename;

    @Column(name = "seal_stored_filename")
    private String sealStoredFilename;

    @Column(name = "seal_content_type")
    private String sealContentType;

    @Column(name = "seal_uploaded_at")
    private LocalDateTime sealUploadedAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "shop")
    private List<User> users;

    // WhatsApp Integration Fields
    @Column(name = "whatsapp_number")
    private String whatsappNumber; // Shop ka WhatsApp business number

    @Column(name = "whatsapp_instance_name")
    private String whatsappInstanceName; // Evolution API instance name (shop_123)

    @Column(name = "whatsapp_qr_code", length = 100000)
    private String whatsappQrCode; // Base64 QR code

    @Column(name = "whatsapp_connected")
    @Builder.Default
    private boolean whatsappConnected = false; // Connection status

    @Column(name = "whatsapp_connected_at")
    private LocalDateTime whatsappConnectedAt;

    private LocalDateTime subscriptionStartDate;

    private LocalDateTime subscriptionEndDate;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isSubscriptionValid() {
        if (subscriptionEndDate == null)
            return true; // Lifetime/FREE plan
        return LocalDateTime.now().isBefore(subscriptionEndDate);
    }

}
