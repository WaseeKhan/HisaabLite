package com.hisaablite.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExpiryReportItem {
    Long batchId;
    Long productId;
    String productName;
    String genericName;
    String manufacturer;
    String supplierName;
    String supplierInvoiceNumber;
    String batchNumber;
    LocalDate expiryDate;
    long daysToExpiry;
    int availableQuantity;
    BigDecimal purchasePrice;
    BigDecimal salePrice;
    BigDecimal mrp;
    String statusLabel;
    String alertLevel;
}
