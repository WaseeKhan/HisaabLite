package com.expygen.entity;

public enum SupportRootCause {
    BILLING("Billing"),
    SUBSCRIPTION("Subscription"),
    LOGIN_ACCESS("Login / Access"),
    INVENTORY("Inventory / Stock"),
    SALES("Sales / Invoice"),
    WHATSAPP("WhatsApp"),
    PERFORMANCE("Performance"),
    DATA_ISSUE("Data Issue"),
    USER_ERROR("User Error"),
    CONFIGURATION("Configuration"),
    OTHER("Other");

    private final String displayName;

    SupportRootCause(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
