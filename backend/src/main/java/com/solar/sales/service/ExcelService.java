package com.solar.sales.service;

import com.solar.sales.dto.ParsedRow;
import com.solar.sales.dto.SaleRequest;
import com.solar.sales.entity.InstallationStatus;
import com.solar.sales.entity.SolarSale;
import com.solar.sales.exception.InvalidExcelException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/** Reads purchase records from Excel and writes sales out to Excel (template + export). */
@Service
public class ExcelService {

    private static final int HEADER_SEARCH_ROWS = 10;
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("d-M-uuuu"),
            DateTimeFormatter.ofPattern("d/M/uuuu"),
            DateTimeFormatter.ofPattern("d.M.uuuu"),
            DateTimeFormatter.ofPattern("d-MMM-uuuu", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH));

    // ---------------------------------------------------------------- reading

    public List<ParsedRow> parse(InputStream in) {
        try (Workbook workbook = WorkbookFactory.create(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new InvalidExcelException("The workbook has no sheets");
            }
            Sheet sheet = workbook.getSheetAt(0);
            int headerRowIndex = findHeaderRow(sheet);
            Map<ExcelColumn, Integer> columns = mapColumns(sheet.getRow(headerRowIndex));
            List<String> missing = Arrays.stream(ExcelColumn.values())
                    .filter(c -> c.required() && !columns.containsKey(c))
                    .map(ExcelColumn::header)
                    .toList();
            if (!missing.isEmpty()) {
                throw new InvalidExcelException("Missing required column(s): " + String.join(", ", missing));
            }

            List<ParsedRow> rows = new ArrayList<>();
            for (int r = headerRowIndex + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (isBlank(row)) {
                    continue;
                }
                rows.add(parseRow(row, columns));
            }
            return rows;
        } catch (InvalidExcelException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new InvalidExcelException("Could not read the file. Please upload a valid .xlsx or .xls workbook.", e);
        }
    }

    private int findHeaderRow(Sheet sheet) {
        int last = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_ROWS);
        for (int r = sheet.getFirstRowNum(); r <= last; r++) {
            Row row = sheet.getRow(r);
            if (row != null && mapColumns(row).size() >= 2) {
                return r;
            }
        }
        throw new InvalidExcelException("Could not find a header row. Download the template to see the expected columns.");
    }

    private Map<ExcelColumn, Integer> mapColumns(Row header) {
        Map<ExcelColumn, Integer> columns = new EnumMap<>(ExcelColumn.class);
        if (header == null) {
            return columns;
        }
        for (Cell cell : header) {
            if (cell.getCellType() == CellType.STRING) {
                ExcelColumn.match(cell.getStringCellValue())
                        .ifPresent(col -> columns.putIfAbsent(col, cell.getColumnIndex()));
            }
        }
        return columns;
    }

    private ParsedRow parseRow(Row row, Map<ExcelColumn, Integer> columns) {
        RowReader reader = new RowReader(row, columns);
        SaleRequest sale = new SaleRequest(
                reader.text(ExcelColumn.INVOICE_NUMBER),
                reader.date(ExcelColumn.PURCHASE_DATE, "purchaseDate"),
                reader.text(ExcelColumn.CUSTOMER_NAME),
                reader.text(ExcelColumn.PHONE),
                reader.text(ExcelColumn.EMAIL),
                reader.text(ExcelColumn.CITY),
                reader.text(ExcelColumn.STATE),
                reader.text(ExcelColumn.PANEL_TYPE),
                reader.decimal(ExcelColumn.CAPACITY_KW, "systemCapacityKw"),
                reader.integer(ExcelColumn.PANEL_COUNT, "panelCount"),
                reader.text(ExcelColumn.INVERTER_BRAND),
                reader.decimal(ExcelColumn.TOTAL_AMOUNT, "totalAmount"),
                reader.decimal(ExcelColumn.AMOUNT_PAID, "amountPaid"),
                reader.installationStatus(),
                reader.date(ExcelColumn.INSTALLATION_DATE, "installationDate"),
                reader.text(ExcelColumn.SALESPERSON));
        return new ParsedRow(row.getRowNum() + 1, sale, reader.errors, reader.failedFields);
    }

    private static boolean isBlank(Row row) {
        if (row == null) {
            return true;
        }
        for (Cell cell : row) {
            Object v = cellValue(cell);
            if (v != null) {
                return false;
            }
        }
        return true;
    }

    /** Returns String, BigDecimal, LocalDate or null. Formula cells use their cached result. */
    private static Object cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> {
                String s = cell.getStringCellValue().trim();
                yield s.isEmpty() ? null : s;
            }
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate()
                    : BigDecimal.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private static final class RowReader {
        private final Row row;
        private final Map<ExcelColumn, Integer> columns;
        private final List<String> errors = new ArrayList<>();
        private final Set<String> failedFields = new HashSet<>();

        RowReader(Row row, Map<ExcelColumn, Integer> columns) {
            this.row = row;
            this.columns = columns;
        }

        private Object raw(ExcelColumn col) {
            Integer idx = columns.get(col);
            return idx == null ? null : cellValue(row.getCell(idx));
        }

        private void fail(ExcelColumn col, String field, Object value, String expected) {
            errors.add(col.header() + ": '" + value + "' is not " + expected);
            failedFields.add(field);
        }

        String text(ExcelColumn col) {
            Object v = raw(col);
            if (v instanceof BigDecimal d) {
                // e.g. phone numbers typed as numbers: 9876543210.0 -> "9876543210"
                return d.stripTrailingZeros().toPlainString();
            }
            return v == null ? null : v.toString();
        }

        BigDecimal decimal(ExcelColumn col, String field) {
            Object v = raw(col);
            if (v == null || v instanceof BigDecimal) {
                return (BigDecimal) v;
            }
            String cleaned = v.toString().replaceAll("(?i)rs\\.?|inr|kw|[₹$,\\s]", "");
            try {
                return new BigDecimal(cleaned);
            } catch (NumberFormatException e) {
                fail(col, field, v, "a valid number");
                return null;
            }
        }

        Integer integer(ExcelColumn col, String field) {
            int before = errors.size();
            BigDecimal d = decimal(col, field);
            if (d == null || errors.size() > before) {
                return null;
            }
            try {
                return d.stripTrailingZeros().intValueExact();
            } catch (ArithmeticException e) {
                fail(col, field, d.toPlainString(), "a whole number");
                return null;
            }
        }

        LocalDate date(ExcelColumn col, String field) {
            Object v = raw(col);
            if (v == null || v instanceof LocalDate) {
                return (LocalDate) v;
            }
            if (v instanceof BigDecimal serial) {
                // A date typed into a "General"-formatted cell arrives as its Excel serial number
                double d = serial.doubleValue();
                if (DateUtil.isValidExcelDate(d) && d > 1) {
                    return DateUtil.getLocalDateTime(d).toLocalDate();
                }
            } else {
                for (DateTimeFormatter f : DATE_FORMATS) {
                    try {
                        return LocalDate.parse(v.toString(), f);
                    } catch (DateTimeParseException ignored) {
                        // try the next format
                    }
                }
            }
            fail(col, field, v, "a valid date (use YYYY-MM-DD or DD-MM-YYYY)");
            return null;
        }

        InstallationStatus installationStatus() {
            String v = text(ExcelColumn.INSTALLATION_STATUS);
            if (v == null) {
                return null;
            }
            try {
                return InstallationStatus.fromText(v);
            } catch (IllegalArgumentException e) {
                errors.add(ExcelColumn.INSTALLATION_STATUS.header() + ": " + e.getMessage());
                failedFields.add("installationStatus");
                return null;
            }
        }
    }

    // ---------------------------------------------------------------- writing

    public byte[] template() {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet("Purchases");
            writeHeader(sheet, styles);

            Row example = sheet.createRow(1);
            Object[] values = {"INV-1001", LocalDate.now().minusDays(7), "Ramesh Kumar", "9876543210",
                    "ramesh@example.com", "Pune", "Maharashtra", "Monocrystalline", 5, 12, "Luminous",
                    325000, 100000, "Scheduled", LocalDate.now().plusDays(5), "Anita Sharma"};
            for (int i = 0; i < values.length; i++) {
                setCell(example.createCell(i), values[i], styles);
            }

            Sheet help = wb.createSheet("Instructions");
            String[] lines = {
                    "How to fill the Purchases sheet",
                    "",
                    "• Keep the header row. Columns can be in any order; extra columns are ignored.",
                    "• Required columns: " + Arrays.stream(ExcelColumn.values()).filter(ExcelColumn::required)
                            .map(ExcelColumn::header).collect(Collectors.joining(", ")),
                    "• Invoice No must be unique — rows whose invoice already exists are skipped.",
                    "• Dates: use an Excel date, or text as YYYY-MM-DD / DD-MM-YYYY / DD/MM/YYYY.",
                    "• Amounts may include ₹ or commas (e.g. ₹3,25,000).",
                    "• Installation Status: Pending, Scheduled or Installed (blank = Pending).",
                    "• Payment status is calculated automatically from Total Amount and Amount Paid.",
                    "• Valid rows are imported even if other rows have errors; the errors are listed after upload.",
                    "• Delete the example row before uploading your data."
            };
            for (int i = 0; i < lines.length; i++) {
                help.createRow(i).createCell(0).setCellValue(lines[i]);
            }
            help.getRow(0).getCell(0).setCellStyle(styles.title);
            help.setColumnWidth(0, 110 * 256);
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build template", e);
        }
    }

    public byte[] export(List<SolarSale> sales) {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles styles = new Styles(wb);
            Sheet sheet = wb.createSheet("Solar Sales");
            writeHeader(sheet, styles);
            int extra = ExcelColumn.values().length;
            sheet.getRow(0).createCell(extra).setCellValue("Balance Due");
            sheet.getRow(0).createCell(extra + 1).setCellValue("Payment Status");
            sheet.getRow(0).getCell(extra).setCellStyle(styles.header);
            sheet.getRow(0).getCell(extra + 1).setCellStyle(styles.header);
            sheet.setColumnWidth(extra, 14 * 256);
            sheet.setColumnWidth(extra + 1, 15 * 256);

            int r = 1;
            for (SolarSale s : sales) {
                Row row = sheet.createRow(r++);
                Object[] values = {s.getInvoiceNumber(), s.getPurchaseDate(), s.getCustomerName(), s.getPhone(),
                        s.getEmail(), s.getCity(), s.getState(), s.getPanelType(), s.getSystemCapacityKw(),
                        s.getPanelCount(), s.getInverterBrand(), s.getTotalAmount(), s.getAmountPaid(),
                        title(s.getInstallationStatus().name()), s.getInstallationDate(), s.getSalesperson(),
                        s.getBalanceDue(), title(s.getPaymentStatus().name())};
                for (int i = 0; i < values.length; i++) {
                    setCell(row.createCell(i), values[i], styles);
                }
            }
            return toBytes(wb);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to build export", e);
        }
    }

    private static void writeHeader(Sheet sheet, Styles styles) {
        Row header = sheet.createRow(0);
        ExcelColumn[] cols = ExcelColumn.values();
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i].header());
            cell.setCellStyle(cols[i].required() ? styles.requiredHeader : styles.header);
            sheet.setColumnWidth(i, cols[i].width() * 256);
        }
        sheet.createFreezePane(0, 1);
    }

    private static void setCell(Cell cell, Object value, Styles styles) {
        switch (value) {
            case null -> { }
            case LocalDate d -> {
                cell.setCellValue(d);
                cell.setCellStyle(styles.date);
            }
            case BigDecimal d -> {
                cell.setCellValue(d.doubleValue());
                cell.setCellStyle(styles.number);
            }
            case Number n -> {
                cell.setCellValue(n.doubleValue());
                cell.setCellStyle(styles.number);
            }
            default -> cell.setCellValue(value.toString());
        }
    }

    private static String title(String enumName) {
        return enumName.charAt(0) + enumName.substring(1).toLowerCase(Locale.ROOT);
    }

    private static byte[] toBytes(Workbook wb) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        wb.write(out);
        return out.toByteArray();
    }

    private static final class Styles {
        final CellStyle header;
        final CellStyle requiredHeader;
        final CellStyle date;
        final CellStyle number;
        final CellStyle title;

        Styles(Workbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            header = wb.createCellStyle();
            header.setFont(bold);
            header.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setBorderBottom(BorderStyle.THIN);

            requiredHeader = wb.createCellStyle();
            requiredHeader.cloneStyleFrom(header);
            requiredHeader.setFillForegroundColor(IndexedColors.GOLD.getIndex());

            CreationHelper helper = wb.getCreationHelper();
            date = wb.createCellStyle();
            date.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
            number = wb.createCellStyle();
            number.setDataFormat(helper.createDataFormat().getFormat("#,##0.##"));

            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            title = wb.createCellStyle();
            title.setFont(big);
        }
    }
}
