package com.renovar.canteiro.io.identity.application;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("security.refresh-cookie")
public record RefreshCookieProperties(
        @NotBlank String name,
        @NotBlank String path,
        boolean secure,
        @NotBlank String sameSite
) {
}
