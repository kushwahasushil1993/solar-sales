package com.solar.sales.service;

import com.solar.sales.dto.*;
import com.solar.sales.dto.InstallationUpdate;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.NotificationType;
import com.solar.sales.entity.PaymentStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.entity.UploadBatch;
import com.solar.sales.exception.BadRequestException;
import com.solar.sales.exception.DuplicateInvoiceException;
import com.solar.sales.exception.InvalidExcelException;
import com.solar.sales.exception.ResourceNotFoundException;
import com.solar.sales.notification.SaleNotificationEvent;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.repository.UploadBatchRepository;
import com.solar.sales.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class SaleService {

    private static final Set<String> SORTABLE = Set.of("purchaseDate", "invoiceNumber", "customerName",
            "systemCapacityKw", "totalAmount", "amountPaid", "createdAt");

    private final SolarSaleRepository saleRepository;
    private final UploadBatchRepository uploadRepository;
    private final ExcelService excelService;
    private final Validator validator;
    private final ApplicationEventPublisher events;

    public SaleService(SolarSaleRepository saleRepository, UploadBatchRepository uploadRepository,
                       ExcelService excelService, Validator validator, ApplicationEventPublisher events) {
        this.saleRepository = saleRepository;
        this.uploadRepository = uploadRepository;
        this.excelService = excelService;
        this.validator = validator;
        this.events = events;
    }

    // ---------------------------------------------------------------- queries

    public record SaleFilter(String search, PaymentStatus paymentStatus, InstallationStatus installationStatus,
                             String panelType, LocalDate from, LocalDate to, Long createdById) {
    }

    @Transactional(readOnly = true)
    public PageResponse<SaleResponse> search(SaleFilter filter, int page, int size, String sortBy, String direction) {
        String sortField = SORTABLE.contains(sortBy) ? sortBy : "purchaseDate";
        Sort sort = Sort.by(Sort.Direction.fromOptionalString(direction).orElse(Sort.Direction.DESC), sortField)
                .and(Sort.by(Sort.Direction.DESC, "id"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 200), sort);
        Page<SolarSale> result = saleRepository.findAll(toSpec(filter), pageable);
        return PageResponse.of(result, SaleResponse::from);
    }

    @Transactional(readOnly = true)
    public List<SolarSale> findAllMatching(SaleFilter filter) {
        return saleRepository.findAll(toSpec(filter), Sort.by(Sort.Direction.DESC, "purchaseDate", "id"));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return SaleResponse.from(find(id));
    }

    private static Specification<SolarSale> toSpec(SaleFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f.search() != null && !f.search().isBlank()) {
                String like = "%" + f.search().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("invoiceNumber")), like),
                        cb.like(cb.lower(root.get("customerName")), like),
                        cb.like(cb.lower(root.get("phone")), like),
                        cb.like(cb.lower(root.get("email")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(cb.lower(root.get("salesperson")), like)));
            }
            if (f.paymentStatus() != null) {
                predicates.add(cb.equal(root.get("paymentStatus"), f.paymentStatus()));
            }
            if (f.installationStatus() != null) {
                predicates.add(cb.equal(root.get("installationStatus"), f.installationStatus()));
            }
            if (f.panelType() != null && !f.panelType().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("panelType")), f.panelType().toLowerCase(Locale.ROOT)));
            }
            if (f.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("purchaseDate"), f.from()));
            }
            if (f.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("purchaseDate"), f.to()));
            }
            if (f.createdById() != null) {
                predicates.add(cb.equal(root.get("createdById"), f.createdById()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    // ---------------------------------------------------------------- CRUD

    /** @param notify email the customer their invoice (after commit; skipped if they have no email) */
    @Transactional
    public SaleResponse create(SaleRequest request, AuthUser user, boolean notify) {
        if (saleRepository.existsByInvoiceNumberIgnoreCase(request.invoiceNumber().trim())) {
            throw new DuplicateInvoiceException(request.invoiceNumber());
        }
        SolarSale sale = new SolarSale();
        apply(sale, request);
        sale.setSource("MANUAL");
        sale.setCreatedBy(user.id(), user.fullName());
        if (sale.getInstallationStatus() != InstallationStatus.PENDING) {
            sale.recordInstallationUpdate(user.fullName());
        }
        saleRepository.save(sale);
        if (notify) {
            publish(sale, NotificationType.INVOICE, user);
        }
        return SaleResponse.from(sale);
    }

    /**
     * @param notify email the customer about workflow progress: a new installation status (scheduled, in progress,
     *               installed), and the full invoice whenever an amount changes (payment received / updated invoice).
     */
    @Transactional
    public SaleResponse update(Long id, SaleRequest request, AuthUser user, boolean notify) {
        SolarSale sale = find(id);
        if (saleRepository.existsByInvoiceNumberIgnoreCaseAndIdNot(request.invoiceNumber().trim(), id)) {
            throw new DuplicateInvoiceException(request.invoiceNumber());
        }
        InstallationStatus statusBefore = sale.getInstallationStatus();
        BigDecimal paidBefore = sale.getAmountPaid();
        BigDecimal totalBefore = sale.getTotalAmount();
        apply(sale, request);
        if (sale.getInstallationStatus() != statusBefore) {
            sale.recordInstallationUpdate(user.fullName());
        }
        saleRepository.saveAndFlush(sale);

        if (notify) {
            NotificationType statusEmail = sale.getInstallationStatus() != statusBefore
                    ? NotificationType.forInstallationStatus(sale.getInstallationStatus()) : null;
            if (statusEmail != null) {
                publish(sale, statusEmail, user);
            }
            boolean totalChanged = sale.getTotalAmount().compareTo(totalBefore) != 0;
            int paidChange = sale.getAmountPaid().compareTo(paidBefore);
            if (totalChanged || paidChange != 0) {
                // any amount change sends the full, updated invoice
                publish(sale, !totalChanged && paidChange > 0
                        ? NotificationType.PAYMENT_RECEIVED : NotificationType.INVOICE_UPDATED, user);
            }
        }
        return SaleResponse.from(sale);
    }

    /**
     * Installation progress only; allowed for sales users as well as admins. Marking a sale installed without a
     * date uses today. The customer is emailed about a new status when {@code notify} is set.
     */
    @Transactional
    public SaleResponse updateInstallation(Long id, InstallationUpdate update, AuthUser user, boolean notify) {
        SolarSale sale = find(id);
        InstallationStatus status = update.installationStatus();
        LocalDate date = update.installationDate();
        if (status == InstallationStatus.INSTALLED && date == null) {
            date = LocalDate.now();
        }
        if (date != null && date.isBefore(sale.getPurchaseDate())) {
            throw new BadRequestException("installationDate", "Installation date cannot be before the purchase date");
        }
        InstallationStatus before = sale.getInstallationStatus();
        sale.setInstallationStatus(status);
        sale.setInstallationDate(date);
        if (status != before) {
            sale.recordInstallationUpdate(user.fullName());
        }
        saleRepository.saveAndFlush(sale);

        if (notify && status != before) {
            NotificationType email = NotificationType.forInstallationStatus(status);
            if (email != null) {
                publish(sale, email, user);
            }
        }
        return SaleResponse.from(sale);
    }

    private void publish(SolarSale sale, NotificationType type, AuthUser user) {
        events.publishEvent(new SaleNotificationEvent(sale.getId(), type, user.fullName()));
    }

    @Transactional
    public void delete(Long id) {
        saleRepository.delete(find(id));
    }

    private SolarSale find(Long id) {
        return saleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sale " + id + " not found"));
    }

    private static void apply(SolarSale sale, SaleRequest r) {
        sale.setInvoiceNumber(r.invoiceNumber().trim());
        sale.setPurchaseDate(r.purchaseDate());
        sale.setCustomerName(r.customerName().trim());
        sale.setPhone(blankToNull(r.phone()));
        sale.setEmail(blankToNull(r.email()));
        sale.setCity(blankToNull(r.city()));
        sale.setState(blankToNull(r.state()));
        sale.setPanelType(blankToNull(r.panelType()));
        sale.setSystemCapacityKw(r.systemCapacityKw());
        sale.setPanelCount(r.panelCount());
        sale.setInverterBrand(blankToNull(r.inverterBrand()));
        sale.setTotalAmount(r.totalAmount());
        sale.setAmountPaid(r.amountPaid() == null ? BigDecimal.ZERO : r.amountPaid());
        sale.setInstallationStatus(r.installationStatus() == null ? InstallationStatus.PENDING : r.installationStatus());
        sale.setInstallationDate(r.installationDate());
        sale.setSalesperson(blankToNull(r.salesperson()));
        sale.refreshPaymentStatus();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    // ---------------------------------------------------------------- Excel upload

    /**
     * Imports every valid row and reports the invalid ones. With {@code dryRun} nothing is saved, so the
     * UI can show a validation preview before the user confirms the import. Customers are only emailed their
     * invoice when {@code emailInvoices} is set (imports are often historical records).
     */
    @Transactional
    public UploadResult importExcel(MultipartFile file, boolean dryRun, AuthUser user, boolean emailInvoices) {
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.xlsx");
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (file.isEmpty()) {
            throw new InvalidExcelException("The uploaded file is empty");
        }
        if (!lower.endsWith(".xlsx") && !lower.endsWith(".xls")) {
            throw new InvalidExcelException("Only Excel files (.xlsx or .xls) are supported");
        }

        List<ParsedRow> rows;
        try (InputStream in = file.getInputStream()) {
            rows = excelService.parse(in);
        } catch (IOException e) {
            throw new InvalidExcelException("Could not read the uploaded file", e);
        }
        if (rows.isEmpty()) {
            throw new InvalidExcelException("No data rows found below the header row");
        }

        Set<String> invoices = new HashSet<>();
        rows.stream().map(r -> r.sale().invoiceNumber())
                .filter(Objects::nonNull)
                .forEach(inv -> invoices.add(inv.trim().toLowerCase(Locale.ROOT)));
        Set<String> existing = invoices.isEmpty() ? Set.of() : saleRepository.findExistingInvoices(invoices);

        Map<String, Integer> firstSeenRow = new HashMap<>();
        List<RowError> errors = new ArrayList<>();
        List<SolarSale> valid = new ArrayList<>();

        for (ParsedRow row : rows) {
            List<String> rowErrors = new ArrayList<>(row.errors());
            validator.validate(row.sale()).stream()
                    .filter(v -> !row.failedFields().contains(v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .forEach(rowErrors::add);

            String invoice = row.sale().invoiceNumber();
            if (invoice != null && !invoice.isBlank()) {
                String key = invoice.trim().toLowerCase(Locale.ROOT);
                if (existing.contains(key)) {
                    rowErrors.add("Invoice '" + invoice.trim() + "' already exists");
                } else {
                    Integer first = firstSeenRow.putIfAbsent(key, row.rowNumber());
                    if (first != null) {
                        rowErrors.add("Duplicate invoice in file (first seen on row " + first + ")");
                    }
                }
            }

            if (rowErrors.isEmpty()) {
                SolarSale sale = new SolarSale();
                apply(sale, row.sale());
                sale.setSource("EXCEL");
                sale.setCreatedBy(user.id(), user.fullName());
                valid.add(sale);
            } else {
                errors.add(new RowError(row.rowNumber(), invoice, String.join("; ", rowErrors)));
            }
        }

        Long batchId = null;
        if (!dryRun) {
            saleRepository.saveAll(valid);
            if (emailInvoices) {
                valid.forEach(sale -> publish(sale, NotificationType.INVOICE, user));
            }
            batchId = uploadRepository.save(new UploadBatch(fileName, rows.size(), valid.size(), errors.size())).getId();
        }
        return new UploadResult(batchId, fileName, rows.size(), valid.size(), errors.size(), errors);
    }

    @Transactional(readOnly = true)
    public List<UploadBatch> uploadHistory() {
        return uploadRepository.findTop20ByOrderByUploadedAtDesc();
    }
}
