package com.hisaablite.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPlanDTO {
    private String planName;
    private Double price;
    private String displayPrice;
    private Integer maxUsers;
    private Integer maxProducts;
    private String description;
    private String features;
    private boolean isFree;
    private String badge; 
    
    // Constructor from SubscriptionPlan
    public RegisterPlanDTO(String planName, Double price, Integer maxUsers, 
                          Integer maxProducts, String description, String features) {
        this.planName = planName;
        this.price = price;
        this.displayPrice = price == 0 ? "FREE" : "₹" + price;
        this.maxUsers = maxUsers;
        this.maxProducts = maxProducts;
        this.description = description;
        this.features = features;
        this.isFree = price == 0;
    }
}