package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierImportRowDto {

    private int rowNumber;
    private String supplierName;
    private String contactPerson;
    private String phone;
    private String gstNumber;
    private String status;
    private String message;
}
