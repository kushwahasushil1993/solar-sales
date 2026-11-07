package com.solar.sales.dto;

import java.util.List;

public record UploadResult(
        Long batchId,
        String fileName,
        int totalRows,
        int importedRows,
        int failedRows,
        List<RowError> errors
) {
}
