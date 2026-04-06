package com.hisaablite.dto;

import java.util.Arrays;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ExpiryReportBucket {
    CRITICAL("critical", "Critical"),
    DAYS_30("30d", "Next 30 Days"),
    DAYS_60("60d", "Next 60 Days"),
    DAYS_90("90d", "Next 90 Days"),
    EXPIRED("expired", "Expired");

    private final String code;
    private final String label;

    public static ExpiryReportBucket fromCode(String code) {
        return Arrays.stream(values())
                .filter(bucket -> bucket.code.equalsIgnoreCase(code))
                .findFirst()
                .orElse(CRITICAL);
    }
}
