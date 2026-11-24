package com.renovar.canteiro.io.reporting.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renovar.canteiro.io.reporting.api.CashPaymentsComparisonResponse.Comparison;
import com.renovar.canteiro.io.reporting.api.CashPaymentsComparisonResponse.Period;
import com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CashPaymentsByCategoryResponse(
        @Schema(types = {"string", "null"}, format = "uuid") UUID workId,
        Period currentPeriod, Period previousPeriod, Instant calculatedAt, List<CategoryComparison> categories) {

    @Schema(name = "CashPaymentsCategoryComparison")
    public record CategoryComparison(
            @Schema(types = {"string", "null"}, format = "uuid", description = "Null for uncategorized payments") UUID categoryId,
            @Schema(types = {"string", "null"}) String categoryCode,
            String categoryName,
            @Schema(types = {"boolean", "null"}) Boolean categoryActive,
            CurrencyCode currency, Comparison payments, Comparison workPayments,
            @JsonInclude(JsonInclude.Include.NON_NULL) Comparison companyPayments) { }

    static CashPaymentsByCategoryResponse from(CompareCashPaymentsUseCase.CategoryResult result) {
        var categories = result.categories().stream().map(row -> {
            var category = row.category();
            var position = row.position();
            return new CategoryComparison(category.id(), category.code(),
                    category.id() == null ? "Sem categoria" : category.name(), category.active(), position.currency(),
                    CashPaymentsComparisonResponse.comparison(position.payments()),
                    CashPaymentsComparisonResponse.comparison(position.workPayments()),
                    CashPaymentsComparisonResponse.comparison(position.companyPayments()));
        }).toList();
        return new CashPaymentsByCategoryResponse(result.workId(), CashPaymentsComparisonResponse.period(result.currentPeriod()),
                CashPaymentsComparisonResponse.period(result.previousPeriod()), result.calculatedAt(), categories);
    }
}
