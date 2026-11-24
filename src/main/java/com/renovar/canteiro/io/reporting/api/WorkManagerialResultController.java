package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.ReadWorkManagerialResultUseCase;
import com.renovar.canteiro.io.reporting.application.ReadWorkManagerialResultUseCase.View;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkManagerialResultController {
    private final ReadWorkManagerialResultUseCase results;

    @GetMapping("/api/v1/company/works/{workId}/diary/managerial-result")
    @Operation(operationId = "getWorkManagerialResult",
            summary = "Read recorded projected result and cash for one work, contract or service in one currency")
    public WorkManagerialResultResponse read(
            @PathVariable UUID workId,
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam CurrencyCode currency,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var result = results.read(workId, contractId, serviceId, currency, from, to);
        return new WorkManagerialResultResponse(result.workId(), result.contractId(), result.serviceId(), result.currency(),
                result.from(), result.to(), result.calculatedAt(), ResultViewResponse.from(result.projected()),
                ResultViewResponse.from(result.recordedCash()), LaborCostCoverage.NO_RECONCILED_SOURCE);
    }

    public record WorkManagerialResultResponse(UUID workId, UUID contractId, UUID serviceId, CurrencyCode currency,
                                               LocalDate from, LocalDate to, Instant calculatedAt,
                                               ResultViewResponse projected, ResultViewResponse recordedCash,
                                               LaborCostCoverage effectiveLaborCostCoverage) { }

    public enum LaborCostCoverage { NO_RECONCILED_SOURCE }

    public record ResultViewResponse(ReadWorkManagerialResultUseCase.Coverage coverage,
                                     List<ReadWorkManagerialResultUseCase.CoverageReason> reasons,
                                     String income, String expense, String tax, String attributedResult,
                                     String result, String margin, String directIncome, String directExpense, String directTax) {
        static ResultViewResponse from(View view) {
            return new ResultViewResponse(view.coverage(), view.reasons(), money(view.income()), money(view.expense()),
                    money(view.tax()), money(view.attributedResult()), money(view.result()), money(view.margin()),
                    money(view.directIncome()), money(view.directExpense()), money(view.directTax()));
        }

        private static String money(BigDecimal value) {
            return value == null ? null : value.setScale(2).toPlainString();
        }
    }
}
