package com.solar.sales.entity;

/** Customer emails sent along the sale workflow. */
public enum NotificationType {
    INVOICE("Invoice"),
    INSTALLATION_SCHEDULED("Installation scheduled"),
    INSTALLATION_IN_PROGRESS("Installation in progress"),
    PAYMENT_RECEIVED("Payment received"),
    INVOICE_UPDATED("Updated invoice"),
    INSTALLATION_COMPLETED("Installation completed");

    private final String label;

    NotificationType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** The email to send when a sale moves to this installation status, if any. */
    public static NotificationType forInstallationStatus(InstallationStatus status) {
        return switch (status) {
            case SCHEDULED -> INSTALLATION_SCHEDULED;
            case IN_PROGRESS -> INSTALLATION_IN_PROGRESS;
            case INSTALLED -> INSTALLATION_COMPLETED;
            case PENDING -> null;
        };
    }
}
