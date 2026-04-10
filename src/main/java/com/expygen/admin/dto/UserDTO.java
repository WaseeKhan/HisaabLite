package com.expygen.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {
    private Long id;
    private String name;
    private String username;
    private String phone;
    private String password;
    private String role;
    private Long shopId;
    private String shopName;
    private boolean active;
    private boolean approved;
}