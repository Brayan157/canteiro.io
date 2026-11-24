package com.renovar.canteiro.io.receivables.domain;

import java.time.Instant;
import java.math.BigDecimal;

/** Official cash position, available only when exact invoice ownership can be proved. */
public record InvoiceReceiptPosition(boolean available, Status status, Reason unavailableReason, Instant calculatedAt,
        BigDecimal receivedAmount, BigDecimal outstandingAmount) {
    public enum Status { UNAVAILABLE, UNPAID, PARTIALLY_RECEIVED, RECEIVED, SETTLED_BY_ADJUSTMENTS }
    public enum Reason { INSUFFICIENT_PERMISSION, INVOICE_CANCELLED, INVOICE_NOT_REGISTERED, EXACT_INVOICE_SETTLEMENT_LINK_REQUIRED, HISTORY_REQUIRES_RECONCILIATION }

    public InvoiceReceiptPosition(boolean available, Status status, Reason reason, Instant now) { this(available, status, reason, now, null, null); }

    public static InvoiceReceiptPosition unavailable(InvoiceStatus invoiceStatus, boolean canReadReceipts, Instant now) {
        Reason reason = !canReadReceipts ? Reason.INSUFFICIENT_PERMISSION
                : invoiceStatus == InvoiceStatus.CANCELLED ? Reason.INVOICE_CANCELLED
                : invoiceStatus != InvoiceStatus.REGISTERED ? Reason.INVOICE_NOT_REGISTERED
                : Reason.EXACT_INVOICE_SETTLEMENT_LINK_REQUIRED;
        return new InvoiceReceiptPosition(false, Status.UNAVAILABLE, reason, now);
    }
}
