package com.solar.sales.service;

import com.solar.sales.dto.DashboardSummary;
import com.solar.sales.dto.DashboardSummary.Breakdown;
import com.solar.sales.dto.DashboardSummary.MonthlyPoint;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.repository.SolarSaleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DashboardService {

    private static final int MONTHS = 12;

    private final SolarSaleRepository repository;

    public DashboardService(SolarSaleRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        Object[] totals = repository.totals().getFirst();
        long count = ((Number) totals[0]).longValue();
        BigDecimal revenue = toDecimal(totals[1]);
        BigDecimal collected = toDecimal(totals[2]);
        BigDecimal capacity = toDecimal(totals[3]);

        List<Breakdown> byPanel = repository.breakdownByPanelType().stream()
                .map(r -> new Breakdown((String) r[0], ((Number) r[1]).longValue(), toDecimal(r[2])))
                .toList();
        List<Breakdown> byInstall = repository.breakdownByInstallationStatus().stream()
                .map(r -> new Breakdown(((InstallationStatus) r[0]).name(), ((Number) r[1]).longValue(), toDecimal(r[2])))
                .toList();

        return new DashboardSummary(count, revenue, collected, revenue.subtract(collected).max(BigDecimal.ZERO),
                capacity, repository.countByInstallationStatusNot(InstallationStatus.INSTALLED),
                monthly(), byPanel, byInstall,
                repository.findTop5ByOrderByPurchaseDateDescIdDesc().stream().map(SaleResponse::from).toList());
    }

    /** Last 12 calendar months including the current one; months without sales are zero-filled. */
    private List<MonthlyPoint> monthly() {
        YearMonth current = YearMonth.now();
        YearMonth start = current.minusMonths(MONTHS - 1);
        Map<YearMonth, MonthlyPoint> points = new LinkedHashMap<>();
        for (YearMonth m = start; !m.isAfter(current); m = m.plusMonths(1)) {
            points.put(m, new MonthlyPoint(m.toString(), 0, BigDecimal.ZERO, BigDecimal.ZERO));
        }
        LocalDate from = start.atDay(1);
        for (SolarSale s : repository.findByPurchaseDateGreaterThanEqual(from)) {
            YearMonth m = YearMonth.from(s.getPurchaseDate());
            points.computeIfPresent(m, (k, p) -> new MonthlyPoint(p.month(), p.sales() + 1,
                    p.revenue().add(s.getTotalAmount()), p.capacityKw().add(s.getSystemCapacityKw())));
        }
        return new ArrayList<>(points.values());
    }

    private static BigDecimal toDecimal(Object o) {
        return o instanceof BigDecimal d ? d : new BigDecimal(o.toString());
    }
}
