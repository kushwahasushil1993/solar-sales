package com.solar.sales.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One Excel upload, kept so the UI can show an upload history. */
@Entity
@Table(name = "upload_batches")
public class UploadBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "failed_rows", nullable = false)
    private int failedRows;

    protected UploadBatch() {
    }

    public UploadBatch(String fileName, int totalRows, int importedRows, int failedRows) {
        this.fileName = fileName;
        this.totalRows = totalRows;
        this.importedRows = importedRows;
        this.failedRows = failedRows;
        this.uploadedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getFileName() { return fileName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public int getTotalRows() { return totalRows; }
    public int getImportedRows() { return importedRows; }
    public int getFailedRows() { return failedRows; }
}
