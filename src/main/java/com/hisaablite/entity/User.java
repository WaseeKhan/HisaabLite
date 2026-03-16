package com.hisaablite.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = "username"),
        @UniqueConstraint(columnNames = "phone")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String username; // Email will be username

    @Column(unique = true, nullable = false)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Role role;

     @Column(nullable = false)
    private boolean approved = false;

    @Column(nullable = false)
    private boolean active = false;

     @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    
     @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Subscription fields
    @Enumerated(EnumType.STRING)
    private PlanType currentPlan;
    
    private LocalDateTime subscriptionStartDate;
    
    private LocalDateTime subscriptionEndDate;
    
    private LocalDateTime approvalDate; // When admin approved

    @ManyToOne
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    
     // Helper methods
    public boolean canAccessDashboard() {
        return active && approved;
    }
    public boolean isSubscriptionActive() {
        if (subscriptionEndDate == null) return true; // Lifetime/FREE plan
        return LocalDateTime.now().isBefore(subscriptionEndDate);
    }

    public boolean isTrialPlan() {
        return currentPlan == PlanType.FREE;
    }

    public Long getRemainingDays() {
        if (subscriptionEndDate == null) return null;
        return java.time.Duration.between(LocalDateTime.now(), subscriptionEndDate).toDays();
    }
}