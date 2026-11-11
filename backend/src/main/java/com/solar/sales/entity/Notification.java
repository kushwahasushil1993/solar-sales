package com.solar.sales.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One customer email attempt. Keeps a snapshot of the invoice number and customer name (no foreign key), so the
 * history survives if the sale is deleted.
 */
@Entity
@Table(name = "notifications", indexes = @Index(name = "idx_notifications_sale", columnList = "sale_id"))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(name = "invoice_number", nullable = false, length = 50)
    private String invoiceNumber;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationType type;

    @Column(length = 150)
    private String recipient;

    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationStatus status;

    @Column(length = 500)
    private String detail;

    @Column(name = "triggered_by", length = 100)
    private String triggeredBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    protected Notification() {
    }

    public Notification(SolarSale sale, NotificationType type, String subject, String triggeredBy) {
        this.saleId = sale.getId();
        this.invoiceNumber = sale.getInvoiceNumber();
        this.customerName = sale.getCustomerName();
        this.recipient = sale.getEmail();
        this.type = type;
        this.subject = subject;
        this.triggeredBy = triggeredBy;
        this.status = NotificationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markSent() {
        status = NotificationStatus.SENT;
        sentAt = LocalDateTime.now();
        detail = null;
    }

    public void markFailed(String reason) {
        status = NotificationStatus.FAILED;
        detail = truncate(reason);
    }

    public void markSkipped(String reason) {
        status = NotificationStatus.SKIPPED;
        detail = truncate(reason);
    }

    private static String truncate(String s) {
        return s == null || s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }

    public Long getId() { return id; }
    public Long getSaleId() { return saleId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getCustomerName() { return customerName; }
    public NotificationType getType() { return type; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public NotificationStatus getStatus() { return status; }
    public String getDetail() { return detail; }
    public String getTriggeredBy() { return triggeredBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
}
