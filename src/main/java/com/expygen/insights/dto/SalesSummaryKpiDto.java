package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesSummaryKpiDto {
    private Double totalSales;
    private Long invoiceCount;
    private Double avgBillValue;
    private Double gstCollected;
    private Double totalDiscount;
    private Long cancelledBills;
}