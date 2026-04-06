package com.hisaablite.dto;

import java.util.List;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BatchDashboardSummary {
    long batchManagedMedicines;
    long liveBatchCount;
    long sellableBatchUnits;
    long criticalExpiryBatchCount;
    long criticalExpiryUnits;
    long nearExpiryBatchCount;
    long expiredBatchCount;
    List<ExpiringBatchSnapshot> expiringBatches;
}
