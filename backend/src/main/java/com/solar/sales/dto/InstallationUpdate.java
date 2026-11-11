package com.solar.sales.dto;

import com.solar.sales.entity.InstallationStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** What a sales user may change on a sale: only the installation progress. */
public record InstallationUpdate(
        @NotNull(message = "Installation status is required")
        InstallationStatus installationStatus,
        LocalDate installationDate
) {
}
