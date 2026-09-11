package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.UUID;

public record ChangeRequestFilter(
        String search,
        AuditModule module,
        ChangeRequestOperation operation,
        UUID requesterUserId,
        LocalDate createdFrom,
        LocalDate createdTo
) {

    public ChangeRequestFilter {
        search = normalizeSearch(search);
        if (createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_FILTER,
                    "createdFrom must be on or before createdTo"
            );
        }
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("[\\p{Cntrl}]", " ").trim();
        if (normalized.length() > 160) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, "search must contain at most 160 characters");
        }
        return normalized.isEmpty() ? null : normalized;
    }
}
