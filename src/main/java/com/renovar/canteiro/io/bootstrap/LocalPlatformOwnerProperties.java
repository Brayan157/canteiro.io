package com.renovar.canteiro.io.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bootstrap.platform-owner")
public record LocalPlatformOwnerProperties(
        boolean enabled,
        String email,
        String password
) {
}
