package com.renovar.canteiro.io.receivables.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class InvoiceReceiptPositionTest {
    @Test void minimizesWithoutFinancialPermissionForEveryDocumentStatus() {
        for (InvoiceStatus status : InvoiceStatus.values()) {
            var result = InvoiceReceiptPosition.unavailable(status, false, Instant.EPOCH);
            assertFalse(result.available());
            assertEquals(InvoiceReceiptPosition.Status.UNAVAILABLE, result.status());
            assertEquals(InvoiceReceiptPosition.Reason.INSUFFICIENT_PERMISSION, result.unavailableReason());
        }
    }

    @Test void documentaryStateNeverProvesPayment() {
        assertEquals(InvoiceReceiptPosition.Reason.EXACT_INVOICE_SETTLEMENT_LINK_REQUIRED,
                InvoiceReceiptPosition.unavailable(InvoiceStatus.REGISTERED, true, Instant.EPOCH).unavailableReason());
        assertEquals(InvoiceReceiptPosition.Reason.INVOICE_CANCELLED,
                InvoiceReceiptPosition.unavailable(InvoiceStatus.CANCELLED, true, Instant.EPOCH).unavailableReason());
        assertEquals(InvoiceReceiptPosition.Reason.INVOICE_NOT_REGISTERED,
                InvoiceReceiptPosition.unavailable(InvoiceStatus.DRAFT, true, Instant.EPOCH).unavailableReason());
    }
}
