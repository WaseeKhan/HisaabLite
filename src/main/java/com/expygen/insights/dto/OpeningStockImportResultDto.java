package com.expygen.insights.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpeningStockImportResultDto {

    private String fileName;
    private int totalRows;
    private int importedCount;
    private int skippedCount;
    private int failedCount;
    private List<OpeningStockImportRowDto> rows;
}
