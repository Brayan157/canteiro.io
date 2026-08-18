package com.renovar.canteiro.io.platform.subscription.domain;

import com.renovar.canteiro.io.platform.subscription.application.PaymentGatewayBillingMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformChargeSynchronizationTest {

    @Test
    void ignoresAnOlderGatewayEventAndPreservesConfirmedPayment() {
        PlatformCharge charge = charge();
        Instant confirmation = Instant.parse("2026-08-17T15:00:00Z");

        assertTrue(charge.applyGatewayStatus(PlatformChargeStatus.CONFIRMED, confirmation));
        assertFalse(charge.applyGatewayStatus(
                PlatformChargeStatus.OVERDUE, Instant.parse("2026-08-17T14:59:59Z")
        ));

        assertEquals(PlatformChargeStatus.CONFIRMED, charge.getStatus());
        assertEquals(confirmation, charge.getLastGatewayEventAt());
    }

    private PlatformCharge charge() {
        return PlatformCharge.create(
                UUID.randomUUID(), UUID.randomUUID(), new PaymentGatewayProviderCode("ASAAS"),
                "key", "cus_1", "pay_1", PaymentGatewayBillingMethod.PIX,
                new BigDecimal("99.90"), LocalDate.of(2026, 8, 17), PlatformChargeStatus.PENDING
        );
    }
}
