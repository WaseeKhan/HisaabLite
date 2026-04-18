package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
@AllArgsConstructor
public class ExpiryLossRowDto {
    private final Long batchId;
    private final String productName;
    private final String batchNumber;
    private final String manufacturer;
    private final Integer availableQty;
    private final LocalDate expiryDate;
    private final Long daysFromToday;
    private final Double purchasePrice;
    private final Double mrp;
    private final Double salePrice;
    private final Double estimatedCostLoss;
    private final Double retailValueAtRisk;
    private final String status;
}
