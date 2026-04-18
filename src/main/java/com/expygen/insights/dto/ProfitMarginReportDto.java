package com.expygen.insights.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfitMarginReportDto {

    private List<InsightsSummaryCardDto> kpis;
    private List<PaymentModeSummaryDto> manufacturerProfitSplit;
    private List<TopProductSummaryDto> topProfitProducts;
    private List<ProfitMarginRowDto> rows;
}
