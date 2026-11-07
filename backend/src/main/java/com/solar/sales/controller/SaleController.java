package com.solar.sales.controller;

import com.solar.sales.dto.PageResponse;
import com.solar.sales.dto.SaleRequest;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.dto.UploadResult;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.PaymentStatus;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.ExcelService;
import com.solar.sales.service.SaleService;
import com.solar.sales.service.SaleService.SaleFilter;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SaleService saleService;
    private final ExcelService excelService;

    public SaleController(SaleService saleService, ExcelService excelService) {
        this.saleService = saleService;
        this.excelService = excelService;
    }

    @GetMapping
    public PageResponse<SaleResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) InstallationStatus installationStatus,
            @RequestParam(required = false) String panelType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "purchaseDate") String sortBy,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(defaultValue = "false") boolean mine,
            @AuthenticationPrincipal AuthUser user) {
        SaleFilter filter = new SaleFilter(search, paymentStatus, installationStatus, panelType, from, to,
                mine ? user.id() : null);
        return saleService.search(filter, page, size, sortBy, direction);
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@Valid @RequestBody SaleRequest request, @AuthenticationPrincipal AuthUser user) {
        return saleService.create(request, user);
    }

    @PutMapping("/{id}")
    public SaleResponse update(@PathVariable Long id, @Valid @RequestBody SaleRequest request) {
        return saleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        saleService.delete(id);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@RequestParam("file") MultipartFile file,
                               @RequestParam(defaultValue = "false") boolean dryRun,
                               @AuthenticationPrincipal AuthUser user) {
        return saleService.importExcel(file, dryRun, user);
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return excel(excelService.template(), "solar-purchase-template.xlsx");
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) InstallationStatus installationStatus,
            @RequestParam(required = false) String panelType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        SaleFilter filter = new SaleFilter(search, paymentStatus, installationStatus, panelType, from, to, null);
        byte[] bytes = excelService.export(saleService.findAllMatching(filter));
        return excel(bytes, "solar-sales-" + LocalDate.now() + ".xlsx");
    }

    private static ResponseEntity<byte[]> excel(byte[] bytes, String fileName) {
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(bytes);
    }
}
