package com.renovar.canteiro.io.platform.subscription.infrastructure.asaas;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AsaasSandboxPropertiesTest {

    @Test
    void rejectsProductionUrlAndProductionKeyInTheSandboxAdapter() {
        assertThrows(IllegalArgumentException.class, () -> new AsaasSandboxProperties(
                URI.create("https://api.asaas.com/v3"),
                "$aact_prod_secret",
                "webhook-secret",
                "canteiro.io/test",
                ZoneId.of("America/Sao_Paulo")
        ));
    }
}
