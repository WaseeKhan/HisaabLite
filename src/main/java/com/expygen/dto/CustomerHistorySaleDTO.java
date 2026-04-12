package com.expygen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHistorySaleDTO {
    private Long saleId;
    private LocalDateTime saleDate;
    private BigDecimal totalAmount;
    private String doctorName;
    private boolean prescriptionRequired;
}
