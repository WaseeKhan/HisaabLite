package com.hisaablite.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleHistoryDTO {
    private Long id;
    private LocalDateTime saleDate;
    private BigDecimal totalAmount;
    private String customerName;
    private String customerPhone;
    private String cashierName;
    private String status;
    private boolean batchManaged;
    private int tracedBatchCount;
    private int tracedUnits;
    private LocalDate nextExpiryDate;
    private int expiredBatchCount;
}
