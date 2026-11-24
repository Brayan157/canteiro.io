package com.renovar.canteiro.io.reporting.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase;
import com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase.ComparisonStatus;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CashPaymentsComparisonResponse(
        @Schema(types = {"string", "null"}, format = "uuid") UUID workId,
        Period currentPeriod, Period previousPeriod, Instant calculatedAt, List<CurrencyPosition> totalsByCurrency) {
    @Schema(name = "CashPaymentsComparisonPeriod")
    public record Period(LocalDate from, LocalDate to) { }

    @Schema(name = "CashPaymentsComparisonCurrency")
    public record CurrencyPosition(CurrencyCode currency, Comparison payments, Comparison workPayments,
            @JsonInclude(JsonInclude.Include.NON_NULL) Comparison companyPayments) { }

    @Schema(name = "CashPaymentsComparisonMovement")
    public record Comparison(PeriodTotal current, PeriodTotal previous,
            @Schema(description = "RECORDED_MOVEMENTS compares recorded movements only. INSUFFICIENT_RECORDS means one or both periods have no entries. NON_POSITIVE_BASE excludes percentage change.") ComparisonStatus status,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "25.00") BigDecimal differenceAmount,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "12.50",
                    description = "Relative change as a percentage without a percent sign. Null for missing records or a non-positive previous total.") BigDecimal percentageChange) { }

    @Schema(name = "CashPaymentsComparisonPeriodTotal")
    public record PeriodTotal(
            @Schema(description = "Scope of available evidence. Recorded entries do not certify the completeness of the period.") PeriodCoverage coverage,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(type = "string", format = "decimal", example = "200.00",
                    description = "Signed total of recorded payments and reversals. Zero with no entries does not prove historical coverage.") BigDecimal amount,
            long entryCount) { }

    public enum PeriodCoverage { RECORDED_MOVEMENTS, NO_RECORDED_MOVEMENTS }

    static CashPaymentsComparisonResponse from(CompareCashPaymentsUseCase.Result result) {
        return new CashPaymentsComparisonResponse(result.workId(), period(result.currentPeriod()), period(result.previousPeriod()),
                result.calculatedAt(), result.totalsByCurrency().stream().map(row -> new CurrencyPosition(row.currency(),
                        comparison(row.payments()), comparison(row.workPayments()), comparison(row.companyPayments()))).toList());
    }

    static Period period(CompareCashPaymentsUseCase.Period value) {
        return new Period(value.from(), value.to());
    }

    static Comparison comparison(CompareCashPaymentsUseCase.Comparison value) {
        if (value == null) return null;
        return new Comparison(total(value.current()), total(value.previous()), value.status(),
                value.differenceAmount(), value.percentageChange());
    }

    private static PeriodTotal total(CompareCashPaymentsUseCase.PeriodTotal value) {
        var coverage = value.entryCount() > 0 ? PeriodCoverage.RECORDED_MOVEMENTS : PeriodCoverage.NO_RECORDED_MOVEMENTS;
        return new PeriodTotal(coverage, value.amount(), value.entryCount());
    }
}
