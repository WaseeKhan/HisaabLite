package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class ExpiryLossReportDto {
    private final List<InsightsSummaryCardDto> kpis;
    private final List<PaymentModeSummaryDto> bucketValueSplit;
    private final List<PaymentModeSummaryDto> manufacturerLossSplit;
    private final List<ExpiryLossRowDto> rows;
}
