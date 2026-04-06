package com.hisaablite.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExpiringBatchSnapshot {
    Long productId;
    String productName;
    String batchNumber;
    LocalDate expiryDate;
    int availableQuantity;
}
