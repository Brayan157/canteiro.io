package com.renovar.canteiro.io.notifications.domain;

public record EmailNotification(String recipient, String subject, String content) {

    public EmailNotification {
        recipient = require(recipient, "Notification recipient is required", 255);
        subject = require(subject, "Notification subject is required", 255);
        content = require(content, "Notification content is required", 10_000);
    }

    private static String require(String value, String message, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
