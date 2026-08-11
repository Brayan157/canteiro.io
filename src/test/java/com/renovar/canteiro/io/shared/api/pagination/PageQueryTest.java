package com.renovar.canteiro.io.shared.api.pagination;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageQueryTest {

    @Test
    void appliesDefaultsWhenPaginationParametersAreAbsent() {
        Pageable pageable = new PageQuery(null, null, null).toPageable(Set.of("createdAt"));

        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertTrue(pageable.getSort().isUnsorted());
    }

    @Test
    void rejectsSortFieldOutsideTheEndpointAllowlist() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> new PageQuery(0, 20, List.of("document,asc")).toPageable(Set.of("createdAt"))
        );

        assertEquals(ErrorCode.INVALID_PAGINATION, exception.getErrorCode());
    }
}
