package com.hisaablite.entity;

public enum TicketStatus {
    OPEN("Open"),
    IN_PROGRESS("In Progress"),
    WAITING_CUSTOMER("Pending"),
    RESOLVED("Resolved"),
    CLOSED("Closed");

    private final String displayName;

    TicketStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}