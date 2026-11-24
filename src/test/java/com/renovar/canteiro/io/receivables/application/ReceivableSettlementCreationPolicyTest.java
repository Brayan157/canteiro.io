package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReceivableSettlementCreationPolicyTest {
    @Test void isFailClosedWithoutAnInvoicePresenceOrConfigurationBypass() {
        var policy = new ReceivableSettlementCreationPolicy();
        assertFalse(policy.isAvailable());
        assertEquals("SETTLEMENT_INVOICE_LINK_REQUIRED", policy.blockReason());
        var error = assertThrows(ApiException.class, policy::requireAvailable);
        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        assertEquals(ErrorCode.BUSINESS_RULE_VIOLATION, error.getErrorCode());
        assertEquals(policy.blockReason(), error.getReason());
    }
}
