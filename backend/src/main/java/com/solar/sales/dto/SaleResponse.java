package com.solar.sales.dto;

import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.PaymentStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.entity.TaskState;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SaleResponse(
        Long id,
        String invoiceNumber,
        LocalDate purchaseDate,
        String customerName,
        String phone,
        String email,
        String city,
        String state,
        String panelType,
        BigDecimal systemCapacityKw,
        Integer panelCount,
        String inverterBrand,
        BigDecimal totalAmount,
        BigDecimal amountPaid,
        BigDecimal balanceDue,
        PaymentStatus paymentStatus,
        InstallationStatus installationStatus,
        LocalDate installationDate,
        String salesperson,
        String source,
        String createdByName,
        String installationUpdatedBy,
        LocalDateTime installationUpdatedAt,
        LocalDateTime createdAt,
        Long agentId,
        String agentName,
        TaskState taskState,
        LocalDateTime assignedAt,
        LocalDateTime acceptedAt
) {
    public static SaleResponse from(SolarSale s) {
        return new SaleResponse(s.getId(), s.getInvoiceNumber(), s.getPurchaseDate(), s.getCustomerName(),
                s.getPhone(), s.getEmail(), s.getCity(), s.getState(), s.getPanelType(), s.getSystemCapacityKw(),
                s.getPanelCount(), s.getInverterBrand(), s.getTotalAmount(), s.getAmountPaid(), s.getBalanceDue(),
                s.getPaymentStatus(), s.getInstallationStatus(), s.getInstallationDate(), s.getSalesperson(),
                s.getSource(), s.getCreatedByName(), s.getInstallationUpdatedBy(), s.getInstallationUpdatedAt(),
                s.getCreatedAt(), s.getAgentId(), s.getAgentName(), s.getTaskState(), s.getAssignedAt(),
                s.getAcceptedAt());
    }
}
