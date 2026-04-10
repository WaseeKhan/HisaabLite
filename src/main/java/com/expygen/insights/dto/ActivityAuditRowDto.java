package com.expygen.insights.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ActivityAuditRowDto {
    private Long id;
    private LocalDateTime timestamp;
    private String userName;
    private String module;
    private String action;
    private String reference;
    private String details;
    private String priority;
}
