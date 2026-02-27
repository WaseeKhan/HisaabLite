package com.hisaablite.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.*;

import java.time.LocalDateTime;

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
    private String panNumber;   //  Business Unique Identity

    private String gstNumber;

    private String address;

    private String city;

    private String state;

    private String pincode;

    private Integer staffLimit;

    @Enumerated(EnumType.STRING)
    private SubscriptionPlan subscriptionPlan;

    private String upiId;

    private boolean active = true;

    private LocalDateTime createdAt = LocalDateTime.now();
}