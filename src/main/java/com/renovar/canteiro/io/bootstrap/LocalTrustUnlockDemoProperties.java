package com.renovar.canteiro.io.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bootstrap.trust-unlock-demo")
public record LocalTrustUnlockDemoProperties(
        boolean enabled,
        String companyUserEmail,
        String companyUserPassword
) {
}
