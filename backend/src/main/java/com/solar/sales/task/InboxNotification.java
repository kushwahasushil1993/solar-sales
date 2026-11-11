package com.solar.sales.task;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** In-app notification shown in a user's Notifications tab (not the customer emails). */
@Entity
@Table(name = "inbox_notifications", indexes = @Index(name = "idx_inbox_recipient", columnList = "recipient_id"))
public class InboxNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InboxType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, length = 400)
    private String message;

    @Column(name = "sale_id")
    private Long saleId;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected InboxNotification() {
    }

    InboxNotification(Long recipientId, InboxType type, String title, String message, Long saleId, String actorName) {
        this.recipientId = recipientId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.saleId = saleId;
        this.actorName = actorName;
        this.createdAt = LocalDateTime.now();
    }

    void markRead() {
        this.read = true;
    }

    public Long getId() { return id; }
    public Long getRecipientId() { return recipientId; }
    public InboxType getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public Long getSaleId() { return saleId; }
    public String getActorName() { return actorName; }
    public boolean isRead() { return read; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
