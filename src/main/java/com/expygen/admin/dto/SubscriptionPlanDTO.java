package com.expygen.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionPlanDTO {
    private Long id;
    private String planName;
    private Double price;
    private Integer durationInDays;
    private String description;
    private String features;
    private Integer maxUsers;
    private Integer maxProducts;
    private Long shopCount;
    private Double usagePercent;
    private boolean active;
    
    // Constructor matching your existing usage
    public SubscriptionPlanDTO(String planName, Double price, Integer durationInDays, 
                              String description, Integer maxUsers, Integer maxProducts) {
        this.planName = planName;
        this.price = price;
        this.durationInDays = durationInDays;
        this.description = description;
        this.maxUsers = maxUsers;
        this.maxProducts = maxProducts;
        this.shopCount = 0L;
        this.usagePercent = 0.0;
        this.active = true;
    }
}