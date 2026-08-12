package com.renovar.canteiro.io.identity.application;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "security.account-activation")
public record AccountActivationProperties(
        @NotNull Duration tokenTtl,
        String emailFrom
) {
}
