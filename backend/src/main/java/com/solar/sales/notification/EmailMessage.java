package com.solar.sales.notification;

public record EmailMessage(String to, String subject, String html, String text) {
}
