package com.expygen.admin.dto;

import com.expygen.entity.PlanType;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AdminShopRequest {
    
    private Long id;
    
    @NotBlank(message = "Shop name is required")
    private String shopName;
    
    @NotBlank(message = "Owner name is required")
    private String ownerName;
    
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String username;
    
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
    private String phone;
    
    private String gstNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String upiId;
    
    @NotNull(message = "Please select a subscription plan")
    private PlanType planType = PlanType.FREE;
    
    private Boolean active = true;
    private LocalDateTime createdAt = LocalDateTime.now();
}
