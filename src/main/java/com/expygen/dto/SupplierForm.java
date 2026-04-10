package com.expygen.dto;

import lombok.Data;

@Data
public class SupplierForm {
    private Long id;
    private String name;
    private String contactPerson;
    private String phone;
    private String gstNumber;
    private String address;
    private String notes;
}
