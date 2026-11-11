package com.solar.sales.entity;

public enum NotificationStatus {
    /** Recorded, being sent. */
    PENDING,
    /** Accepted by the mail server. */
    SENT,
    /** Sending failed; see detail. Can be resent. */
    FAILED,
    /** Not sent on purpose, e.g. the customer has no email or SMTP is not configured; see detail. */
    SKIPPED
}
