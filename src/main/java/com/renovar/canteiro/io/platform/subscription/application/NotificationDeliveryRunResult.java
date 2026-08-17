package com.renovar.canteiro.io.platform.subscription.application;

public record NotificationDeliveryRunResult(int claimed, int delivered, int failed, int cancelled) {

    public NotificationDeliveryRunResult {
        if (claimed < 0 || delivered < 0 || failed < 0 || cancelled < 0) {
            throw new IllegalArgumentException("Notification delivery result cannot be negative");
        }
    }
}
