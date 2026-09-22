package com.renovar.canteiro.io.identity.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.password-reset")
public record PasswordResetProperties(
        @NotNull Duration tokenTtl,
        String emailFrom,
        String frontendPasswordResetUrl
) {
    public PasswordResetProperties {
        if (frontendPasswordResetUrl == null || frontendPasswordResetUrl.isBlank()) {
            throw new IllegalArgumentException("Password reset frontend URL is required");
        }
        URI uri = URI.create(frontendPasswordResetUrl);
        if (!uri.isAbsolute() || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("Password reset frontend URL must be an absolute HTTP(S) URL without a fragment");
        }
    }
}
