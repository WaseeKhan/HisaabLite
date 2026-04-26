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
@Table(name = "subscription_plans")
public class SubscriptionPlan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "plan_name", nullable = false, unique = true)
    private String planName;
    
    @Column(name = "price", nullable = false)
    private Double price;

    @Column(name = "annual_price")
    private Double annualPrice;

    @Column(name = "annual_discount_percent")
    private Double annualDiscountPercent;
    
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

    @Transient
    public Double getEffectiveAnnualPrice() {
        if (annualPrice != null && annualPrice >= 0) {
            return annualPrice;
        }
        if (price == null || price <= 0) {
            return 0.0;
        }

        double discount = annualDiscountPercent == null ? 0.0 : Math.max(0.0, Math.min(100.0, annualDiscountPercent));
        return Math.round((price * 12.0d) * (1.0d - (discount / 100.0d)) * 100.0d) / 100.0d;
    }

    @Transient
    public Double getAnnualListPrice() {
        if (price == null || price <= 0) {
            return 0.0;
        }
        return price * 12.0d;
    }

    @Transient
    public Double getEffectiveAnnualDiscountPercent() {
        Double listPrice = getAnnualListPrice();
        Double effectivePrice = getEffectiveAnnualPrice();
        if (listPrice == null || listPrice <= 0 || effectivePrice == null || effectivePrice <= 0) {
            return 0.0;
        }
        double discount = ((listPrice - effectivePrice) / listPrice) * 100.0d;
        return Math.max(0.0, Math.round(discount * 10.0d) / 10.0d);
    }
}
