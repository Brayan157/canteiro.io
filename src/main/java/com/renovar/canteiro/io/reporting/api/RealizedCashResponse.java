package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.ReadRealizedCashUseCase;
import com.renovar.canteiro.io.reporting.application.ReadRealizedCashUseCase.Coverage;
import com.renovar.canteiro.io.shared.api.json.CanonicalMoneySerializer;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.media.Schema;
import tools.jackson.databind.annotation.JsonSerialize;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RealizedCashResponse(
        @Schema(types = {"string", "null"}, format = "uuid", description = "Null in the company view.") UUID workId,
        @Schema(types = {"string", "null"}, format = "date",
                description = "Requested start date, or the first authorized movement in the accumulated view. Null if that view has no movements.")
        LocalDate from, LocalDate to, Instant calculatedAt, List<CurrencyPosition> totalsByCurrency) {

    @Schema(name = "RealizedCashCurrencyPosition")
    public record CurrencyPosition(CurrencyCode currency, Component receipts, Component payments, Component workPayments,
            @Schema(description = "Company-wide expenses. Omitted from the work view.")
            @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Component companyPayments,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "120.00",
                    description = "Signed receipts minus signed payments. Null when either permission is missing.") BigDecimal netMovement,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "25.00",
                    description = "Work cash result divided by positive net recorded receipts, multiplied by 100. Null in the company view, when a source is unauthorized, or when net receipts are zero or negative.")
            BigDecimal cashResultPercentage) { }

    @Schema(name = "RealizedCashComponent")
    public record Component(
            @Schema(description = "RECORDED_MOVEMENTS covers official recorded entries only, with no assurance of complete bank history. NO_PERMISSION hides the amount and count.") Coverage coverage,
            @JsonSerialize(using = CanonicalMoneySerializer.class)
            @Schema(types = {"string", "null"}, format = "decimal", example = "-20.00") BigDecimal amount,
            @Schema(types = {"integer", "null"}, format = "int64") Long entryCount) { }

    static RealizedCashResponse from(ReadRealizedCashUseCase.Result result) {
        return new RealizedCashResponse(result.workId(), result.from(), result.to(), result.calculatedAt(),
                result.totalsByCurrency().stream().map(row -> new CurrencyPosition(row.currency(), component(row.receipts()),
                        component(row.payments()), component(row.workPayments()), component(row.companyPayments()),
                        row.netMovement(), row.cashResultPercentage())).toList());
    }

    private static Component component(ReadRealizedCashUseCase.Component value) {
        return value == null ? null : new Component(value.coverage(), value.amount(), value.entryCount());
    }
}
