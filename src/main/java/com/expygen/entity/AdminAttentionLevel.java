package com.expygen.entity;

public enum AdminAttentionLevel {
    NORMAL("Healthy"),
    WATCH("Watch"),
    PRIORITY("Priority"),
    BLOCKED("Blocked");

    private final String displayName;

    AdminAttentionLevel(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
