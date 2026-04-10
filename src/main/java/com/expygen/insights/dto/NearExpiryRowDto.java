package com.expygen.insights.dto;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NearExpiryRowDto {
    private Long batchId;
    private String productName;
    private String batchNumber;
    private String manufacturer;
    private Integer availableQty;
    private LocalDate expiryDate;
    private Long daysLeft;
    private Double mrp;
    private Double stockValue;
    private String status;
}
