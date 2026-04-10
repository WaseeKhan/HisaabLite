package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class PaymentModeSummaryDto {
    private String paymentMode;
    private Double amount;

    public String getLabel() {
        return paymentMode;
    }
}
