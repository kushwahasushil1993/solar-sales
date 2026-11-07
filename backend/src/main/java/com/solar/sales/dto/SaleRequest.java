package com.solar.sales.dto;

import com.solar.sales.entity.InstallationStatus;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload for creating/updating a sale; also the target of each parsed Excel row. */
public record SaleRequest(
        @NotBlank(message = "Invoice number is required")
        @Size(max = 50, message = "Invoice number must be at most 50 characters")
        String invoiceNumber,

        @NotNull(message = "Purchase date is required")
        @PastOrPresent(message = "Purchase date cannot be in the future")
        LocalDate purchaseDate,

        @NotBlank(message = "Customer name is required")
        @Size(max = 150, message = "Customer name must be at most 150 characters")
        String customerName,

        @Size(max = 20, message = "Phone must be at most 20 characters")
        String phone,

        @Email(message = "Email is not valid")
        @Size(max = 150)
        String email,

        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 50) String panelType,

        @NotNull(message = "System capacity (kW) is required")
        @DecimalMin(value = "0.1", message = "System capacity must be at least 0.1 kW")
        BigDecimal systemCapacityKw,

        @PositiveOrZero(message = "Panel count cannot be negative")
        Integer panelCount,

        @Size(max = 100) String inverterBrand,

        @NotNull(message = "Total amount is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Total amount must be greater than 0")
        BigDecimal totalAmount,

        @PositiveOrZero(message = "Amount paid cannot be negative")
        BigDecimal amountPaid,

        InstallationStatus installationStatus,
        LocalDate installationDate,

        @Size(max = 100) String salesperson
) {
    @AssertTrue(message = "Amount paid cannot exceed total amount")
    public boolean isAmountPaidWithinTotal() {
        return amountPaid == null || totalAmount == null || amountPaid.compareTo(totalAmount) <= 0;
    }

    @AssertTrue(message = "Installation date cannot be before purchase date")
    public boolean isInstallationAfterPurchase() {
        return installationDate == null || purchaseDate == null || !installationDate.isBefore(purchaseDate);
    }
}
