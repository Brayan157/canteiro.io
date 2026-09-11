package com.renovar.canteiro.io.identity.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.net.URI;

@Validated
@ConfigurationProperties(prefix = "security.account-activation")
public record AccountActivationProperties(
        @NotNull Duration tokenTtl,
        String emailFrom,
        String frontendActivationUrl
) {
    public AccountActivationProperties {
        if (frontendActivationUrl == null || frontendActivationUrl.isBlank()) {
            throw new IllegalArgumentException("Account activation frontend URL is required");
        }
        URI uri = URI.create(frontendActivationUrl);
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Account activation frontend URL must be an absolute HTTP(S) URL without a fragment");
        }
    }
}
