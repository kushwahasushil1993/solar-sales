package com.solar.sales.controller;

import com.solar.sales.dto.InstallationUpdate;
import com.solar.sales.dto.NotificationResponse;
import com.solar.sales.dto.PageResponse;
import com.solar.sales.dto.SaleRequest;
import com.solar.sales.dto.SaleResponse;
import com.solar.sales.dto.UploadResult;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.notification.NotificationService;
import com.solar.sales.entity.PaymentStatus;
import com.solar.sales.security.AuthUser;
import com.solar.sales.service.ExcelService;
import com.solar.sales.service.SaleService;
import com.solar.sales.service.SaleService.SaleFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import java.util.List;

@Tag(name = "Sales", description = "Solar sales records, Excel upload and export")
@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final SaleService saleService;
    private final ExcelService excelService;
    private final NotificationService notificationService;

    public SaleController(SaleService saleService, ExcelService excelService, NotificationService notificationService) {
        this.saleService = saleService;
        this.excelService = excelService;
        this.notificationService = notificationService;
    }

    @Operation(summary = "Search sales (paged)")
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
            @Parameter(description = "Only sales created by the current user")
            @RequestParam(defaultValue = "false") boolean mine,
            @AuthenticationPrincipal AuthUser user) {
        SaleFilter filter = new SaleFilter(search, paymentStatus, installationStatus, panelType, from, to,
                mine ? user.id() : null);
        return saleService.search(filter, page, size, sortBy, direction);
    }

    @Operation(summary = "Get one sale")
    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @Operation(summary = "Add a sale")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponse create(@Valid @RequestBody SaleRequest request,
                               @Parameter(description = "Email the invoice to the customer")
                               @RequestParam(defaultValue = "true") boolean notify,
                               @AuthenticationPrincipal AuthUser user) {
        return saleService.create(request, user, notify);
    }

    @Operation(summary = "Update a sale (admin)")
    @PutMapping("/{id}")
    public SaleResponse update(@PathVariable Long id, @Valid @RequestBody SaleRequest request,
                               @Parameter(description = "Email the customer about a new installation status or payment")
                               @RequestParam(defaultValue = "true") boolean notify,
                               @AuthenticationPrincipal AuthUser user) {
        return saleService.update(id, request, user, notify);
    }

    @Operation(summary = "Update installation status and date (sales users and admins)",
            description = "Only the installation progress changes. INSTALLED without a date uses today. A new status "
                    + "emails the customer unless notify=false.")
    @PatchMapping("/{id}/installation")
    public SaleResponse updateInstallation(@PathVariable Long id, @Valid @RequestBody InstallationUpdate update,
                                           @Parameter(description = "Email the customer about the new status")
                                           @RequestParam(defaultValue = "true") boolean notify,
                                           @AuthenticationPrincipal AuthUser user) {
        return saleService.updateInstallation(id, update, user, notify);
    }

    @Operation(summary = "Delete a sale (admin)")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        saleService.delete(id);
    }

    @Operation(summary = "Upload purchase records from Excel (admin)",
            description = "Valid rows are imported, invalid rows are reported by Excel row number. "
                    + "With dryRun=true nothing is saved.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResult upload(@Parameter(description = ".xlsx or .xls file, max 10 MB")
                               @RequestParam("file") MultipartFile file,
                               @Parameter(description = "Validate only, don't save")
                               @RequestParam(defaultValue = "false") boolean dryRun,
                               @Parameter(description = "Email each imported customer their invoice")
                               @RequestParam(defaultValue = "false") boolean emailInvoices,
                               @AuthenticationPrincipal AuthUser user) {
        return saleService.importExcel(file, dryRun, user, emailInvoices);
    }

    @Operation(summary = "Customer emails sent for this sale, newest first (admin)")
    @GetMapping("/{id}/notifications")
    public List<NotificationResponse> notifications(@PathVariable Long id) {
        return notificationService.forSale(id);
    }

    @Operation(summary = "Email the invoice to the customer now (admin)")
    @PostMapping("/{id}/notifications/invoice")
    public NotificationResponse sendInvoice(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return notificationService.sendNow(id, NotificationType.INVOICE, user.fullName());
    }

    @Operation(summary = "Download the Excel upload template (admin)")
    @GetMapping("/template")
    public ResponseEntity<byte[]> template() {
        return excel(excelService.template(), "solar-purchase-template.xlsx");
    }

    @Operation(summary = "Export sales matching the filters to Excel (admin)")
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
