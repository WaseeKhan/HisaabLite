package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class FastSlowMovementRowDto {
    private final Long productId;
    private final String productName;
    private final String manufacturer;
    private final String barcode;
    private final Integer currentStock;
    private final Long unitsSold;
    private final Long invoiceCount;
    private final Double revenue;
    private final Double avgDailyUnits;
    private final LocalDateTime lastSoldAt;
    private final Double stockCoverDays;
    private final String movementStatus;
}
