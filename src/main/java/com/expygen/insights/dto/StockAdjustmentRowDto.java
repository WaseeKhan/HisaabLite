package com.expygen.insights.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StockAdjustmentRowDto {
    private Long id;
    private LocalDateTime timestamp;
    private String productName;
    private String batchNumber;
    private String adjustmentType;
    private Integer previousQty;
    private Integer changedQty;
    private Integer newQty;
    private String reason;
    private String userName;
}
