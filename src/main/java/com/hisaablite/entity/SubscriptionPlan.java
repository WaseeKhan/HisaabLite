package com.hisaablite.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plan_name", nullable = false, unique = true)
    private String planName;
    
    @Column(name = "price", nullable = false)
    private Double price;
    
    @Column(name = "duration_in_days")
    private Integer durationInDays;
    
    @Column(name = "description")
    private String description;
    
    @Column(name = "features")
    private String features;
    
    @Column(name = "max_users")
    private Integer maxUsers;
    
    @Column(name = "max_products")
    private Integer maxProducts;
    
    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
