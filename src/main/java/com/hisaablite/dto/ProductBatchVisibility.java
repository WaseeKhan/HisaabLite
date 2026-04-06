package com.hisaablite.dto;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductBatchVisibility {
    Long productId;
    boolean batchManaged;
    int activeBatchCount;
    int liveBatchStock;
    int sellableStock;
    int nearExpiryBatchCount;
    int expiredBatchCount;
    LocalDate nextSellableExpiryDate;
    boolean lowStock;
}
