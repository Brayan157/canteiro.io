package com.renovar.canteiro.io.shared.api.pagination;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Set;

public record PageQuery(Integer page, Integer size, List<String> sort) {

    public static final int DEFAULT_PAGE = 0;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public Pageable toPageable(Set<String> allowedSortFields) {
        int resolvedPage = page == null ? DEFAULT_PAGE : page;
        int resolvedSize = size == null ? DEFAULT_SIZE : size;

        if (resolvedPage < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page must be zero or greater.");
        }
        if (resolvedSize < 1 || resolvedSize > MAX_SIZE) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_PAGINATION,
                    "Size must be between 1 and " + MAX_SIZE + "."
            );
        }

        return PageRequest.of(resolvedPage, resolvedSize, toSort(allowedSortFields));
    }

    private Sort toSort(Set<String> allowedSortFields) {
        if (sort == null || sort.isEmpty()) {
            return Sort.unsorted();
        }

        List<Sort.Order> orders = normalizedSortValues().stream()
                .map(value -> toOrder(value, allowedSortFields))
                .toList();
        return Sort.by(orders);
    }

    private List<String> normalizedSortValues() {
        if (sort.stream().anyMatch(value -> value.contains(","))) {
            return sort;
        }
        if (sort.size() % 2 != 0) {
            throw invalidSortFormat();
        }

        return java.util.stream.IntStream.range(0, sort.size() / 2)
                .mapToObj(index -> sort.get(index * 2) + "," + sort.get(index * 2 + 1))
                .toList();
    }

    private Sort.Order toOrder(String value, Set<String> allowedSortFields) {
        String[] parts = value.split(",", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw invalidSortFormat();
        }
        if (!allowedSortFields.contains(parts[0])) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Sort field is not allowed.");
        }

        try {
            return new Sort.Order(Sort.Direction.fromString(parts[1]), parts[0]);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.INVALID_PAGINATION,
                    "Sort direction must be asc or desc."
            );
        }
    }

    private ApiException invalidSortFormat() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_PAGINATION,
                "Sort must use the format field,asc or field,desc."
        );
    }
}
