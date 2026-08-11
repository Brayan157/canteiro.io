package com.renovar.canteiro.io.shared.api.filter;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class FilterQueryParser {

    public List<FilterCriterion> parse(List<String> rawFilters, Set<String> allowedFields) {
        if (rawFilters == null || rawFilters.isEmpty()) {
            return List.of();
        }
        return rawFilters.stream().map(value -> parse(value, allowedFields)).toList();
    }

    private FilterCriterion parse(String value, Set<String> allowedFields) {
        String[] parts = value.split(":", 3);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_FILTER,
                    "Filter must use the format field:operator:value."
            );
        }
        if (!allowedFields.contains(parts[0])) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, "Filter field is not allowed.");
        }

        try {
            return new FilterCriterion(parts[0], FilterOperator.valueOf(parts[1].toUpperCase(Locale.ROOT)), parts[2]);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_FILTER, "Filter operator is not allowed.");
        }
    }
}
