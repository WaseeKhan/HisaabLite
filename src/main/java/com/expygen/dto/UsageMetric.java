package com.expygen.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsageMetric {
    private String key;
    private String label;
    private long used;
    private Integer limit;
    private long remaining;
    private int percentage;
    private boolean unlimited;
    private String helperText;
    private String tone;
}
