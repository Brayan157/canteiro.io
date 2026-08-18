package com.renovar.canteiro.io.notifications.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "notifications.delivery")
public record NotificationDeliveryProperties(
        @Min(1) int batchSize,
        @NotNull Duration retryAfter
) {
}
