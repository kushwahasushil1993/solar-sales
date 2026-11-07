package com.solar.sales.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardSummary(
        long totalSales,
        BigDecimal totalRevenue,
        BigDecimal amountCollected,
        BigDecimal outstandingBalance,
        BigDecimal totalCapacityKw,
        long pendingInstallations,
        List<MonthlyPoint> monthly,
        List<Breakdown> byPanelType,
        List<Breakdown> byInstallationStatus,
        List<SaleResponse> recentSales
) {
    public record MonthlyPoint(String month, long sales, BigDecimal revenue, BigDecimal capacityKw) {
    }

    public record Breakdown(String label, long count, BigDecimal value) {
    }
}
