package com.renovar.canteiro.io.contracts.domain;

import java.util.UUID;

public record ContractBillingControlFilter(UUID contractId, ContractStatus status, String query) {
    public String normalizedQuery() {
        return query == null || query.isBlank() ? null : query.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
