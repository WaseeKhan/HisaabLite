package com.expygen.insights.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitMarginRowDto {

    private Long productId;
    private String productName;
    private String manufacturer;
    private Long quantitySold;
    private BigDecimal revenue;
    private BigDecimal estimatedCost;
    private BigDecimal grossProfit;
    private Double marginPercent;
    private LocalDateTime lastSoldAt;
}
