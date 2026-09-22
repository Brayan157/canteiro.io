package com.renovar.canteiro.io.works.application;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Tenant-scoped criteria for operational work listings. */
public record WorkFilter(
        String name,
        String reference,
        UUID finalCustomerId,
        String finalCustomerName,
        String finalCustomerDocument,
        WorkStatus status,
        WorkExecutionLocationType executionLocationType,
        LocalDate startedFrom,
        LocalDate startedTo,
        LocalDate expectedCompletionFrom,
        LocalDate expectedCompletionTo
) {
    public WorkFilter {
        validateRange(startedFrom, startedTo, "startedFrom must not be after startedTo");
        validateRange(expectedCompletionFrom, expectedCompletionTo,
                "expectedCompletionFrom must not be after expectedCompletionTo");
    }

    public String normalizedName() { return normalize(name); }
    public String normalizedReference() { return normalize(reference); }
    public String normalizedFinalCustomerName() { return normalize(finalCustomerName); }

    public String normalizedFinalCustomerDocument() {
        if (finalCustomerDocument == null || finalCustomerDocument.isBlank()) {
            return null;
        }
        String normalized = finalCustomerDocument.replaceAll("\\D", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateRange(LocalDate from, LocalDate to, String message) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, message);
        }
    }
}
