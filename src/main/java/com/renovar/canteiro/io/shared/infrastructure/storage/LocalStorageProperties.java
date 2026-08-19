package com.renovar.canteiro.io.shared.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storage.local")
public record LocalStorageProperties(String basePath) {
}
