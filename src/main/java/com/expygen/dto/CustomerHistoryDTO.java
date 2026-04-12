package com.expygen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerHistoryDTO {
    private boolean found;
    private String customerName;
    private String customerPhone;
    private Long visitCount;
    private BigDecimal lifetimeSpend;
    private LocalDateTime lastVisitDate;
    private String lastDoctorName;
    private List<String> recentMedicines;
    private List<CustomerHistorySaleDTO> recentSales;
}
