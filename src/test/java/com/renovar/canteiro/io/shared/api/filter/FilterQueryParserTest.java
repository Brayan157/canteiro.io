package com.renovar.canteiro.io.shared.api.filter;

import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FilterQueryParserTest {

    private final FilterQueryParser parser = new FilterQueryParser();

    @Test
    void parsesAnAllowedFilter() {
        List<FilterCriterion> filters = parser.parse(List.of("active:EQ:true"), Set.of("active"));

        assertEquals(List.of(new FilterCriterion("active", FilterOperator.EQ, "true")), filters);
    }

    @Test
    void rejectsFilterFieldOutsideTheEndpointAllowlist() {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> parser.parse(List.of("email:CONTAINS:example"), Set.of("active"))
        );

        assertEquals(ErrorCode.INVALID_FILTER, exception.getErrorCode());
    }
}
