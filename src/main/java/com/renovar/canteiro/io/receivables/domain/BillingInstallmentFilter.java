package com.renovar.canteiro.io.receivables.domain;

import java.time.LocalDate;
import java.util.UUID;

public record BillingInstallmentFilter(UUID contractId, String reference, BillingInstallmentStatus status,
                                      LocalDate dueFrom, LocalDate dueTo) {
    public BillingInstallmentFilter {
        reference = reference == null || reference.isBlank() ? null : reference.trim();
        if (reference != null && reference.length() > 100) throw new IllegalArgumentException("Reference filter must have at most 100 characters");
        if (dueFrom != null && dueTo != null && dueFrom.isAfter(dueTo)) {
            throw new IllegalArgumentException("Due date range is invalid");
        }
    }
}
