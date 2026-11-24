package com.renovar.canteiro.io.receivables.domain;

import java.time.LocalDate;

public record ReceivableSettlementFilter(ReceivableSettlementEntryType entryType,
        ReceivableSettlementStatus status, ReceivableSettlementPaymentMethod paymentMethod,
        LocalDate receivedFrom, LocalDate receivedTo) {
    public ReceivableSettlementFilter {
        if (receivedFrom != null && receivedTo != null && receivedFrom.isAfter(receivedTo)) {
            throw new IllegalArgumentException("Settlement date range is invalid");
        }
    }
}
