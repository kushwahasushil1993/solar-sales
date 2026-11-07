package com.solar.sales.entity;

import java.util.Arrays;

public enum InstallationStatus {
    PENDING, SCHEDULED, INSTALLED;

    /** Lenient parse used by the Excel importer ("Installed", " scheduled ", etc.). */
    public static InstallationStatus fromText(String text) {
        String normalized = text.trim().toUpperCase();
        return Arrays.stream(values())
                .filter(s -> s.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Invalid installation status '" + text + "' (use Pending, Scheduled or Installed)"));
    }
}
