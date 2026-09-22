package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Tenant-scoped criteria for operational contract listings. */
public record ContractFilter(
        String name,
        String reference,
        UUID workId,
        String workName,
        String finalCustomerName,
        ContractStatus status,
        LocalDate startedFrom,
        LocalDate startedTo,
        LocalDate expectedCompletionFrom,
        LocalDate expectedCompletionTo,
        LocalDate completedFrom,
        LocalDate completedTo
) {
    public ContractFilter {
        validateRange(startedFrom, startedTo, "startedFrom must not be after startedTo");
        validateRange(expectedCompletionFrom, expectedCompletionTo,
                "expectedCompletionFrom must not be after expectedCompletionTo");
        validateRange(completedFrom, completedTo, "completedFrom must not be after completedTo");
    }

    public String normalizedName() { return normalize(name); }
    public String normalizedReference() { return normalize(reference); }
    public String normalizedWorkName() { return normalize(workName); }
    public String normalizedFinalCustomerName() { return normalize(finalCustomerName); }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateRange(LocalDate from, LocalDate to, String message) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, message);
        }
    }
}
