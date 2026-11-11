package com.solar.sales.notification;

import com.solar.sales.entity.NotificationType;

/** Published by SaleService; the email goes out only after the sale change is committed. */
public record SaleNotificationEvent(Long saleId, NotificationType type, String triggeredBy) {
}
