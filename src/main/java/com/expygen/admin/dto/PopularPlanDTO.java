package com.expygen.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PopularPlanDTO {
    private String planName;   // FREE, BASIC, PRO
    private Long shopCount;     // Kitne shops is plan par hain
}
