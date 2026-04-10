package com.expygen.insights.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PurchaseSummaryRowDto {
    private Long purchaseId;
    private String invoiceNo;
    private LocalDate purchaseDate;
    private String supplierName;
    private Integer itemCount;
    private Double taxableAmount;
    private Double gstAmount;
    private Double totalAmount;
    private String createdBy;
    private String status;
}
