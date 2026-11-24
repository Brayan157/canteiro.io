package com.renovar.canteiro.io.receivables.domain;

import java.time.LocalDate;
import java.util.UUID;

public record InvoiceFilter(UUID contractId, String number, String series, String contractSearch,
                            String installmentReference, InvoiceStatus status, LocalDate issuedFrom, LocalDate issuedTo) {
    public InvoiceFilter {
        number = text(number, 100);
        series = text(series, 50);
        contractSearch = text(contractSearch, 200);
        installmentReference = text(installmentReference, 100);
        if (issuedFrom != null && issuedTo != null && issuedFrom.isAfter(issuedTo)) {
            throw new IllegalArgumentException("Invoice issue date range is invalid");
        }
    }

    private static String text(String value, int limit) {
        String normalized = value == null || value.isBlank() ? null : value.trim();
        if (normalized != null && normalized.length() > limit) {
            throw new IllegalArgumentException("Invoice filter exceeds its maximum length of " + limit);
        }
        return normalized;
    }
}
