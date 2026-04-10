package com.expygen.dto;

import java.time.LocalDate;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StockAdjustmentForm {

    private LocalDate adjustmentDate;

    private Long purchaseBatchId;

    private Long productId;

    private Integer quantityDelta;

    private String reason;

    private String notes;
}
