package com.renovar.canteiro.io.platform.subscription.infrastructure.asaas;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "integrations.asaas")
public record AsaasSandboxProperties(
        URI baseUrl,
        String apiKey,
        String webhookToken,
        String userAgent,
        ZoneId webhookZone
) {

    public AsaasSandboxProperties {
        if (baseUrl == null || !"https".equalsIgnoreCase(baseUrl.getScheme())
                || !"api-sandbox.asaas.com".equalsIgnoreCase(baseUrl.getHost())) {
            throw new IllegalArgumentException("Asaas sandbox base URL must use https://api-sandbox.asaas.com");
        }
        apiKey = requireSecret(apiKey, "Asaas sandbox API key");
        if (!apiKey.startsWith("$aact_hmlg_")) {
            throw new IllegalArgumentException("Asaas sandbox API key must use the sandbox prefix");
        }
        webhookToken = requireSecret(webhookToken, "Asaas webhook token");
        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("Asaas User-Agent is required");
        }
        userAgent = userAgent.trim();
        if (webhookZone == null) {
            throw new IllegalArgumentException("Asaas webhook time zone is required");
        }
    }

    private static String requireSecret(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value.trim();
    }
}
