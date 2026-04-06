package com.hisaablite.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaleBatchTraceSummaryDTO {
    private boolean batchManaged;
    private int tracedBatchCount;
    private int tracedUnits;
    private LocalDate nextExpiryDate;
    private int expiredBatchCount;
}
