package com.solar.sales.service;

import com.solar.sales.dto.RowError;
import com.solar.sales.dto.UploadResult;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.PaymentStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.exception.InvalidExcelException;
import com.solar.sales.repository.SolarSaleRepository;
import com.solar.sales.repository.UploadBatchRepository;
import com.solar.sales.entity.Role;
import com.solar.sales.security.AuthUser;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.solar.sales.support.TestSenders;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestSenders.class)
class SaleImportTest {

    private static final AuthUser ADMIN = new AuthUser(1L, "admin@test.local", "Test Admin", Role.ADMIN);

    @Autowired SaleService saleService;
    @Autowired ExcelService excelService;
    @Autowired SolarSaleRepository saleRepository;
    @Autowired UploadBatchRepository uploadRepository;

    @BeforeEach
    void clean() {
        saleRepository.deleteAll();
        uploadRepository.deleteAll();
    }

    @Test
    void importsValidRowsAndReportsInvalidOnes() throws Exception {
        byte[] file = workbook(
                // header in a different order, using aliases
                new Object[]{"Customer", "Invoice Number", "Date", "System Size", "Amount", "Paid", "Mobile", "Installation"},
                new Object[]{"Ramesh Kumar", "INV-1", "2026-01-15", 5, "₹3,25,000", 325000, 9876543210L, "installed"},
                new Object[]{"Priya Patel", "INV-2", "15-02-2026", 3.5, 210000, 50000, null, null},
                new Object[]{"Bad Amount", "INV-3", "2026-03-01", 2, "abc", null, null, null},
                new Object[]{"Dup In File", "INV-1", "2026-03-02", 2, 100000, null, null, null},
                new Object[]{null, null, null, null, null, null, null, null}, // blank row is ignored
                new Object[]{"", "INV-4", "2026-03-03", 2, 100000, 200000, null, "Unknown"});

        UploadResult result = saleService.importExcel(xlsx("purchases.xlsx", file), false, ADMIN, false);

        assertThat(result.totalRows()).isEqualTo(5);
        assertThat(result.importedRows()).isEqualTo(2);
        assertThat(result.failedRows()).isEqualTo(3);
        assertThat(result.errors()).extracting(RowError::row).containsExactly(4, 5, 7);
        assertThat(result.errors().get(0).message())
                .contains("Total Amount: 'abc' is not a valid number")
                .doesNotContain("Total amount is required");
        assertThat(result.errors().get(1).message()).contains("first seen on row 2");
        assertThat(result.errors().get(2).message())
                .contains("Customer name is required", "Amount paid cannot exceed total amount", "Invalid installation status");

        SolarSale ramesh = saleRepository.findAll().stream()
                .filter(s -> s.getInvoiceNumber().equals("INV-1")).findFirst().orElseThrow();
        assertThat(ramesh.getTotalAmount()).isEqualByComparingTo("325000");
        assertThat(ramesh.getPhone()).isEqualTo("9876543210");
        assertThat(ramesh.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(ramesh.getInstallationStatus()).isEqualTo(InstallationStatus.INSTALLED);
        assertThat(ramesh.getSource()).isEqualTo("EXCEL");
        assertThat(ramesh.getCreatedByName()).isEqualTo("Test Admin");

        SolarSale priya = saleRepository.findAll().stream()
                .filter(s -> s.getInvoiceNumber().equals("INV-2")).findFirst().orElseThrow();
        assertThat(priya.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 2, 15));
        assertThat(priya.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
        assertThat(priya.getBalanceDue()).isEqualByComparingTo(new BigDecimal("160000"));

        assertThat(uploadRepository.count()).isEqualTo(1);
    }

    @Test
    void reuploadSkipsInvoicesThatAlreadyExist() throws Exception {
        byte[] file = workbook(
                new Object[]{"Invoice No", "Purchase Date", "Customer Name", "Capacity (kW)", "Total Amount"},
                new Object[]{"INV-9", "2026-01-15", "Ramesh", 5, 300000});
        saleService.importExcel(xlsx("a.xlsx", file), false, ADMIN, false);

        UploadResult second = saleService.importExcel(xlsx("a.xlsx", file), false, ADMIN, false);

        assertThat(second.importedRows()).isZero();
        assertThat(second.errors().getFirst().message()).contains("already exists");
        assertThat(saleRepository.count()).isEqualTo(1);
    }

    @Test
    void dryRunValidatesWithoutSaving() throws Exception {
        byte[] file = workbook(
                new Object[]{"Invoice No", "Purchase Date", "Customer Name", "Capacity (kW)", "Total Amount"},
                new Object[]{"INV-5", "2026-01-15", "Ramesh", 5, 300000});

        UploadResult result = saleService.importExcel(xlsx("a.xlsx", file), true, ADMIN, false);

        assertThat(result.importedRows()).isEqualTo(1);
        assertThat(result.batchId()).isNull();
        assertThat(saleRepository.count()).isZero();
        assertThat(uploadRepository.count()).isZero();
    }

    @Test
    void rejectsSheetMissingRequiredColumns() throws Exception {
        byte[] file = workbook(new Object[]{"Invoice No", "Customer Name"}, new Object[]{"INV-1", "Ramesh"});

        assertThatThrownBy(() -> saleService.importExcel(xlsx("a.xlsx", file), false, ADMIN, false))
                .isInstanceOf(InvalidExcelException.class)
                .hasMessageContaining("Purchase Date", "Capacity (kW)", "Total Amount");
    }

    @Test
    void rejectsNonExcelFiles() {
        MockMultipartFile csv = new MockMultipartFile("file", "a.csv", "text/csv", "a,b".getBytes());
        assertThatThrownBy(() -> saleService.importExcel(csv, false, ADMIN, false))
                .isInstanceOf(InvalidExcelException.class)
                .hasMessageContaining(".xlsx");
    }

    @Test
    void templateCanBeReimported() {
        // the template's example row is valid, so uploading the untouched template imports exactly one row
        UploadResult result = saleService.importExcel(xlsx("t.xlsx", excelService.template()), true, ADMIN, false);
        assertThat(result.errors()).isEmpty();
        assertThat(result.importedRows()).isEqualTo(1);
    }

    @Test
    void exportRoundTripsThroughImport() throws Exception {
        byte[] file = workbook(
                new Object[]{"Invoice No", "Purchase Date", "Customer Name", "Capacity (kW)", "Total Amount"},
                new Object[]{"INV-7", "2026-01-15", "Ramesh", 5, 300000});
        saleService.importExcel(xlsx("a.xlsx", file), false, ADMIN, false);
        byte[] exported = excelService.export(saleRepository.findAll());
        saleRepository.deleteAll();

        UploadResult result = saleService.importExcel(xlsx("export.xlsx", exported), false, ADMIN, false);

        assertThat(result.errors()).isEmpty();
        assertThat(saleRepository.findAll()).extracting(SolarSale::getInvoiceNumber).containsExactly("INV-7");
    }

    private static MockMultipartFile xlsx(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", bytes);
    }

    private static byte[] workbook(Object[]... rows) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r);
                for (int c = 0; c < rows[r].length; c++) {
                    Object v = rows[r][c];
                    if (v instanceof Number n) {
                        row.createCell(c).setCellValue(n.doubleValue());
                    } else if (v != null) {
                        row.createCell(c).setCellValue(v.toString());
                    }
                }
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

}
