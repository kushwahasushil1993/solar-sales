package com.solar.sales.task;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Audit trail of installation-task actions (who did what, to which job, for which agent). */
@Entity
@Table(name = "activities", indexes = {
        @Index(name = "idx_activity_agent", columnList = "agent_id"),
        @Index(name = "idx_activity_sale", columnList = "sale_id")
})
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_role", length = 20)
    private String actorRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ActivityAction action;

    @Column(name = "sale_id", nullable = false)
    private Long saleId;

    @Column(name = "invoice_number", length = 50)
    private String invoiceNumber;

    @Column(name = "customer_name", length = 150)
    private String customerName;

    /** The agent the action concerns. */
    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Column(length = 300)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Activity() {
    }

    Activity(Long actorId, String actorName, String actorRole, ActivityAction action, Long saleId,
             String invoiceNumber, String customerName, Long agentId, String agentName, String detail) {
        this.actorId = actorId;
        this.actorName = actorName;
        this.actorRole = actorRole;
        this.action = action;
        this.saleId = saleId;
        this.invoiceNumber = invoiceNumber;
        this.customerName = customerName;
        this.agentId = agentId;
        this.agentName = agentName;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public String getActorRole() { return actorRole; }
    public ActivityAction getAction() { return action; }
    public Long getSaleId() { return saleId; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getCustomerName() { return customerName; }
    public Long getAgentId() { return agentId; }
    public String getAgentName() { return agentName; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
