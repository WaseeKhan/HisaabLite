package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class FastSlowMovementReportDto {
    private final List<InsightsSummaryCardDto> kpis;
    private final List<PaymentModeSummaryDto> movementStatusSplit;
    private final List<PaymentModeSummaryDto> manufacturerUnitsSplit;
    private final List<TopProductSummaryDto> topMovers;
    private final List<FastSlowMovementRowDto> rows;
}
