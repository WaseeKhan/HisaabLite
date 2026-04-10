package com.expygen.insights.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InvoiceSalesRowDto {
    private Long saleId;
    private String invoiceNo;
    private LocalDateTime saleDate;
    private String customerName;
    private String customerPhone;
    private String paymentMode;
    private Double taxableAmount;
    private Double gstAmount;
    private Double discountAmount;
    private Double totalAmount;
    private String status;
}
