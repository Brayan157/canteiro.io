package com.renovar.canteiro.io.platform.subscription.application;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayContractsTest {

    @Test
    void normalizesWebhookHeadersAndDefensivelyCopiesTheRawPayload() {
        byte[] rawPayload = "{\"event\":\"PAYMENT_CONFIRMED\"}".getBytes(StandardCharsets.UTF_8);
        PaymentGatewayWebhookRequest request = new PaymentGatewayWebhookRequest(
                rawPayload, Map.of("X-Signature", "signed-value"), Instant.parse("2026-08-12T10:00:00Z")
        );
        rawPayload[0] = 'X';
        byte[] receivedPayload = request.rawPayload();
        receivedPayload[0] = 'Y';

        assertEquals("signed-value", request.headers().get("x-signature"));
        assertArrayEquals("{\"event\":\"PAYMENT_CONFIRMED\"}".getBytes(StandardCharsets.UTF_8), request.rawPayload());
    }

    @Test
    void rejectsRepeatedWebhookHeaderNamesIgnoringCase() {
        Map<String, String> repeatedHeaders = new LinkedHashMap<>();
        repeatedHeaders.put("X-Signature", "first");
        repeatedHeaders.put("x-signature", "second");

        assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayWebhookRequest(
                "payload".getBytes(StandardCharsets.UTF_8), repeatedHeaders, Instant.now()
        ));
    }

    @Test
    void requiresAnIdempotencyKeyForEveryChargeRequestedFromAGateway() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayChargeRequest(
                UUID.randomUUID(), new BigDecimal("99.90"), LocalDate.of(2026, 8, 20),
                PaymentGatewayBillingMethod.PIX, " "
        ));
    }
}
