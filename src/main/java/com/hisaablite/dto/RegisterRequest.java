package com.hisaablite.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "Shop name is required")
    private String shopName;

    @NotBlank(message = "Owner name is required")
    private String ownerName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Phone is required")
    private String phone;

    @NotBlank(message = "PAN number is required")
    private String panNumber;

    private String gstNumber;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String upiId;
}