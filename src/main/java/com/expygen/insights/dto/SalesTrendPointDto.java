package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SalesTrendPointDto {
    private String label;
    private Double amount;
}