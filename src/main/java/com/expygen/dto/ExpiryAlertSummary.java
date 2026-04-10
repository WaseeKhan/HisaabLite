package com.expygen.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ExpiryAlertSummary {
    long criticalAlertCount;
    long criticalAlertUnits;
    long expiredBatchCount;
    long expiredUnits;
    long expiringIn7DaysCount;
    long expiringIn7DaysUnits;
    long expiringIn30DaysCount;
    long expiringIn30DaysUnits;
    long expiringIn60DaysCount;
    long expiringIn60DaysUnits;
    long expiringIn90DaysCount;
    long expiringIn90DaysUnits;
}
