package com.renovar.canteiro.io.shared.infrastructure.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LocalStorageProperties.class)
class LocalStorageConfiguration {

    @Bean
    StorageProvider storageProvider(LocalStorageProperties properties) {
        return new LocalStorageProvider(properties);
    }
}
