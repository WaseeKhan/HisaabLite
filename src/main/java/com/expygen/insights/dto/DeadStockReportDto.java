package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class DeadStockReportDto {
    private final List<InsightsSummaryCardDto> kpis;
    private final List<PaymentModeSummaryDto> manufacturerSplit;
    private final List<PaymentModeSummaryDto> stockAgeSplit;
    private final List<DeadStockRowDto> rows;
}
