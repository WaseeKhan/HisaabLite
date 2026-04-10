package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class InsightsSummaryCardDto {
    private String label;
    private String value;
    private String description;
}
