package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.customers.domain.FinalCustomerType;

/**
 * Filters accepted by the final-customer operational list. The document is
 * normalized before it reaches persistence, so formatting characters never
 * change the result of a CPF/CNPJ lookup.
 */
public record FinalCustomerFilter(
        String name,
        String document,
        FinalCustomerType customerType,
        Boolean active
) {
    public String normalizedName() {
        return normalizeText(name);
    }

    public String normalizedDocument() {
        if (document == null || document.isBlank()) {
            return null;
        }
        String normalized = document.replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
