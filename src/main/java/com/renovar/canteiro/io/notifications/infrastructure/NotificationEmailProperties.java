package com.renovar.canteiro.io.notifications.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notifications.email")
public record NotificationEmailProperties(String from) {
}
