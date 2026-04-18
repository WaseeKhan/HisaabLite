package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@AllArgsConstructor
public class DeadStockRowDto {
    private final Long productId;
    private final String productName;
    private final String manufacturer;
    private final String barcode;
    private final Integer currentStock;
    private final Double purchasePrice;
    private final Double salePrice;
    private final Double stockValue;
    private final LocalDateTime lastSoldAt;
    private final Long daysSinceLastSale;
    private final String status;
}
