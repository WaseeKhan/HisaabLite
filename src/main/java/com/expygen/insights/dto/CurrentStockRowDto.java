package com.expygen.insights.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentStockRowDto {
    private Long batchId;
    private String productName;
    private String batchNumber;
    private String manufacturer;
    private String barcode;
    private Integer availableQty;
    private Double mrp;
    private Double salePrice;
    private LocalDate expiryDate;
    private Double stockValue;
    private String status;
}
