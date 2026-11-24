package com.renovar.canteiro.io.reporting.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renovar.canteiro.io.reporting.application.ReadCashDueUseCase;
import com.renovar.canteiro.io.reporting.application.ReadCashDueUseCase.Coverage;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CashDueResponse(@Schema(types = {"string", "null"}, format = "uuid") UUID workId,
        LocalDate asOf, LocalDate dueThrough, int horizonDays, Instant calculatedAt, List<CurrencyPosition> totalsByCurrency) {
    @Schema(name = "CashDueCurrencyPosition")
    public record CurrencyPosition(CurrencyCode currency, Component receivables, Component payables, Component workPayables,
            @JsonInclude(JsonInclude.Include.NON_NULL) Component companyPayables) { }

    @Schema(name = "CashDueComponent")
    public record Component(Bucket overdue, Bucket upcoming) { }

    @Schema(name = "CashDueBucket")
    public record Bucket(Coverage coverage,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "120.00") BigDecimal amount,
            @Schema(types = {"integer", "null"}, format = "int64") Long titleCount,
            @Schema(types = {"integer", "null"}, format = "int64") Long unavailableTitleCount) { }

    static CashDueResponse from(ReadCashDueUseCase.Result result) {
        return new CashDueResponse(result.workId(), result.asOf(), result.dueThrough(), result.horizonDays(), result.calculatedAt(),
                result.totalsByCurrency().stream().map(row -> new CurrencyPosition(row.currency(), component(row.receivables()),
                        component(row.payables()), component(row.workPayables()), component(row.companyPayables()))).toList());
    }

    private static Component component(ReadCashDueUseCase.Component value) {
        return value == null ? null : new Component(bucket(value.overdue()), bucket(value.upcoming()));
    }

    private static Bucket bucket(ReadCashDueUseCase.Bucket value) {
        return new Bucket(value.coverage(), value.amount(), value.titleCount(), value.unavailableTitleCount());
    }
}
