package com.expygen.insights.dto;

import lombok.Data;
import java.util.List;

@Data
public class SalesSummaryPageDto {
    private SalesSummaryKpiDto kpis;
    private List<SalesTrendPointDto> salesTrend;
    private List<SalesInvoiceSummaryRowDto> invoices;
    private List<PaymentModeSummaryDto> paymentModes;
    private List<TopProductSummaryDto> topProducts;
}