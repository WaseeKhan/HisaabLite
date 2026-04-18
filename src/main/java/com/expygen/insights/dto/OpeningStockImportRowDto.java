package com.expygen.insights.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningStockImportRowDto {

    private int rowNumber;
    private String medicineName;
    private String barcode;
    private String batchNumber;
    private String status;
    private String message;
}
