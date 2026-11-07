package com.solar.sales.controller;

import com.solar.sales.dto.DashboardSummary;
import com.solar.sales.entity.UploadBatch;
import com.solar.sales.service.DashboardService;
import com.solar.sales.service.SaleService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DashboardController {

    private final DashboardService dashboardService;
    private final SaleService saleService;

    public DashboardController(DashboardService dashboardService, SaleService saleService) {
        this.dashboardService = dashboardService;
        this.saleService = saleService;
    }

    @GetMapping("/dashboard")
    public DashboardSummary dashboard() {
        return dashboardService.summary();
    }

    @GetMapping("/uploads")
    public List<UploadBatch> uploads() {
        return saleService.uploadHistory();
    }
}
