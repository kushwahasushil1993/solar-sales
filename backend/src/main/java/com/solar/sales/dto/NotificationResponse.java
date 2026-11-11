package com.solar.sales.dto;

import com.solar.sales.entity.Notification;
import com.solar.sales.entity.NotificationStatus;
import com.solar.sales.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(Long id, Long saleId, String invoiceNumber, String customerName,
                                   NotificationType type, String typeLabel, String recipient, String subject,
                                   NotificationStatus status, String detail, String triggeredBy,
                                   LocalDateTime createdAt, LocalDateTime sentAt) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getSaleId(), n.getInvoiceNumber(), n.getCustomerName(),
                n.getType(), n.getType().label(), n.getRecipient(), n.getSubject(), n.getStatus(), n.getDetail(),
                n.getTriggeredBy(), n.getCreatedAt(), n.getSentAt());
    }
}
