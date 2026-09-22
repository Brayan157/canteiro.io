package com.renovar.canteiro.io.identity.infrastructure.security;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
@ConfigurationProperties("security.cors")
public record ApiCorsProperties(@NotEmpty List<String> allowedOrigins) {
}
