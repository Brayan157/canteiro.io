package com.renovar.canteiro.io.identity.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.password-reset")
public record PasswordResetProperties(
        @NotNull Duration tokenTtl,
        String emailFrom
) {
}
