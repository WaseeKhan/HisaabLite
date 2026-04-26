package com.expygen.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SubscriptionEntitlementItem {
    String code;
    String label;
    String availability;
    String tone;
    String helperText;
}
