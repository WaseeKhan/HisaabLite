package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TopProductSummaryDto {
    private String productName;
    private Double amount;
}