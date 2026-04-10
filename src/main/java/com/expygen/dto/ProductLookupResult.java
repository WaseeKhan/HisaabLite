package com.expygen.dto;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ProductLookupResult {
    Long id;
    String name;
    String barcode;
    String genericName;
    String manufacturer;
    String packSize;
    BigDecimal price;
    Integer gstPercent;
    Integer sellableStock;
    boolean prescriptionRequired;
}
