package com.expygen.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoldBatchTraceDTO {
    private String batchNumber;
    private Integer quantity;
    private LocalDate expiryDate;
    private boolean expired;
}
