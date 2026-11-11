package com.solar.sales.entity;

import java.util.Locale;

/** Installation workflow: PENDING → SCHEDULED → IN_PROGRESS → INSTALLED. */
public enum InstallationStatus {
    PENDING, SCHEDULED, IN_PROGRESS, INSTALLED;

    /** Lenient parse used by the Excel importer ("Installed", " in progress ", "In-Process", etc.). */
    public static InstallationStatus fromText(String text) {
        String key = text.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        return switch (key) {
            case "PENDING" -> PENDING;
            case "SCHEDULED" -> SCHEDULED;
            case "INPROGRESS", "INPROCESS" -> IN_PROGRESS;
            case "INSTALLED", "COMPLETED", "DONE" -> INSTALLED;
            default -> throw new IllegalArgumentException("Invalid installation status '" + text.trim()
                    + "' (use Pending, Scheduled, In progress or Installed)");
        };
    }
}
