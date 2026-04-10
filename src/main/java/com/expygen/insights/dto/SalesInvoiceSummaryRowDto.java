package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class SalesInvoiceSummaryRowDto {
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
