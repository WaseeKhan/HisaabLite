package com.expygen.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ContactRequestForm {

    @NotBlank(message = "Store name is required")
    private String storeName;

    @NotBlank(message = "Contact person is required")
    private String contactPerson;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email")
    private String email;

    private String phone;

    @NotBlank(message = "Support topic is required")
    private String topic;

    @NotBlank(message = "Message is required")
    private String message;
}