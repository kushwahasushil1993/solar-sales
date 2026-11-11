package com.solar.sales.controller;

import com.solar.sales.dto.DashboardSummary;
import com.solar.sales.entity.UploadBatch;
import com.solar.sales.service.DashboardService;
import com.solar.sales.service.SaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Dashboard", description = "Summary figures and upload history")
@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SaleService saleService;

    public DashboardController(DashboardService dashboardService, SaleService saleService) {
        this.dashboardService = dashboardService;
        this.saleService = saleService;
    }

    @Operation(summary = "KPIs, monthly revenue, breakdowns and recent sales")
    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return dashboardService.summary();
    }

    @Operation(summary = "Last 20 Excel uploads (admin)")
    @GetMapping("/uploads")
    public List<UploadBatch> uploads() {
        return saleService.uploadHistory();
    }
}
