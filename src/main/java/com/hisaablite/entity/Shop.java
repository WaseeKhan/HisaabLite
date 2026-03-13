package com.hisaablite.entity;

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

    @Column(nullable = false, unique = true)
    private String panNumber;

    private String gstNumber;

    private String address;

    private String city;

    private String state;

    private String pincode;

    private Integer staffLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type")
    private PlanType planType = PlanType.FREE;

    private String upiId;

    private boolean active = true;

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
    private boolean whatsappConnected = false; // Connection status

    @Column(name = "whatsapp_connected_at")
    private LocalDateTime whatsappConnectedAt;

}