package com.renovar.canteiro.io.platform.subscription.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentGatewayProviderCodeTest {

    @Test
    void normalizesAProviderCodeWithoutKnowingAConcreteProvider() {
        assertEquals("TEST_GATEWAY", new PaymentGatewayProviderCode(" test_gateway ").value());
    }

    @Test
    void rejectsBlankOrStructurallyInvalidCodes() {
        assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayProviderCode(" "));
        assertThrows(IllegalArgumentException.class, () -> new PaymentGatewayProviderCode("invalid-code"));
    }
}
