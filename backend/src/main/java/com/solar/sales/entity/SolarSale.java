package com.solar.sales.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "solar_sales", indexes = {
        @Index(name = "idx_sales_purchase_date", columnList = "purchase_date"),
        @Index(name = "idx_sales_customer", columnList = "customer_name")
})
public class SolarSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 50)
    private String invoiceNumber;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "customer_name", nullable = false, length = 150)
    private String customerName;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String state;

    @Column(name = "panel_type", length = 50)
    private String panelType;

    @Column(name = "system_capacity_kw", nullable = false, precision = 10, scale = 2)
    private BigDecimal systemCapacityKw;

    @Column(name = "panel_count")
    private Integer panelCount;

    @Column(name = "inverter_brand", length = 100)
    private String inverterBrand;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "amount_paid", nullable = false, precision = 14, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus;

    // LegacySchemaCleanup keeps this a plain VARCHAR column so statuses can be added later
    @Enumerated(EnumType.STRING)
    @Column(name = "installation_status", nullable = false, length = 20)
    private InstallationStatus installationStatus = InstallationStatus.PENDING;

    @Column(name = "installation_date")
    private LocalDate installationDate;

    @Column(length = 100)
    private String salesperson;

    @Column(length = 20)
    private String source;

    /** User who entered the sale (null for demo data). */
    @Column(name = "created_by_id")
    private Long createdById;

    @Column(name = "created_by_name", length = 100)
    private String createdByName;

    /** Who last changed the installation status, and when (sales users and admins). */
    @Column(name = "installation_updated_by", length = 100)
    private String installationUpdatedBy;

    @Column(name = "installation_updated_at")
    private LocalDateTime installationUpdatedAt;

    /** The installation task: which agent handles it (see TaskState). */
    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "task_state", nullable = false, length = 20, columnDefinition = "varchar(20) default 'OPEN'")
    private TaskState taskState = TaskState.OPEN;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        refreshPaymentStatus();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
        refreshPaymentStatus();
    }

    /** Payment status is always derived from the amounts so it can never disagree with them. */
    public void refreshPaymentStatus() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        if (totalAmount != null && paid.compareTo(totalAmount) >= 0) {
            paymentStatus = PaymentStatus.PAID;
        } else if (paid.signum() > 0) {
            paymentStatus = PaymentStatus.PARTIAL;
        } else {
            paymentStatus = PaymentStatus.PENDING;
        }
    }

    public BigDecimal getBalanceDue() {
        BigDecimal paid = amountPaid == null ? BigDecimal.ZERO : amountPaid;
        return totalAmount == null ? BigDecimal.ZERO : totalAmount.subtract(paid).max(BigDecimal.ZERO);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate purchaseDate) { this.purchaseDate = purchaseDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getPanelType() { return panelType; }
    public void setPanelType(String panelType) { this.panelType = panelType; }
    public BigDecimal getSystemCapacityKw() { return systemCapacityKw; }
    public void setSystemCapacityKw(BigDecimal systemCapacityKw) { this.systemCapacityKw = systemCapacityKw; }
    public Integer getPanelCount() { return panelCount; }
    public void setPanelCount(Integer panelCount) { this.panelCount = panelCount; }
    public String getInverterBrand() { return inverterBrand; }
    public void setInverterBrand(String inverterBrand) { this.inverterBrand = inverterBrand; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public InstallationStatus getInstallationStatus() { return installationStatus; }
    public void setInstallationStatus(InstallationStatus installationStatus) { this.installationStatus = installationStatus; }
    public LocalDate getInstallationDate() { return installationDate; }
    public void setInstallationDate(LocalDate installationDate) { this.installationDate = installationDate; }
    public String getSalesperson() { return salesperson; }
    public void setSalesperson(String salesperson) { this.salesperson = salesperson; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getCreatedById() { return createdById; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedBy(Long id, String name) { this.createdById = id; this.createdByName = name; }
    public String getInstallationUpdatedBy() { return installationUpdatedBy; }
    public LocalDateTime getInstallationUpdatedAt() { return installationUpdatedAt; }
    public void recordInstallationUpdate(String byName) {
        this.installationUpdatedBy = byName;
        this.installationUpdatedAt = LocalDateTime.now();
    }
    public Long getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public TaskState getTaskState() { return taskState; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }

    /** Admin assigns the task; the agent still has to accept it. */
    public void assignTo(Long agentId, String agentName) {
        this.agentId = agentId;
        this.agentName = agentName;
        this.taskState = TaskState.ASSIGNED;
        this.assignedAt = LocalDateTime.now();
        this.acceptedAt = null;
    }

    public void markAccepted() {
        this.taskState = TaskState.ACCEPTED;
        this.acceptedAt = LocalDateTime.now();
    }

    public void unassign() {
        this.agentId = null;
        this.agentName = null;
        this.taskState = TaskState.OPEN;
        this.assignedAt = null;
        this.acceptedAt = null;
    }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
