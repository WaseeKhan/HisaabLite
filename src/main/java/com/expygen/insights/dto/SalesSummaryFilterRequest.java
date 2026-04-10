package com.expygen.insights.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SalesSummaryFilterRequest {
    private LocalDate fromDate;
    private LocalDate toDate;
    private String paymentMode;
    private String keyword;
}