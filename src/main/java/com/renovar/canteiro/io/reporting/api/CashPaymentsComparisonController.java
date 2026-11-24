package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.CompareCashPaymentsUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/v1/company/cash-flow", produces = "application/json")
@Tag(name = "Cash flow")
@ApiResponses({
    @ApiResponse(responseCode = "200", useReturnTypeSchema = true),
    @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
})
public class CashPaymentsComparisonController {
    private final CompareCashPaymentsUseCase payments;

    @GetMapping("/payments/comparison")
    @Operation(operationId = "compareCompanyCashPayments", summary = "Compares recorded company payments with the immediately preceding period",
            description = "Requires PAYABLES.READ. Inclusive effective dates through today UTC. The preceding window has equal duration. Separates currencies and WORK/COMPANY. Totals cover only recorded payments and reversals.")
    public CashPaymentsComparisonResponse company(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CashPaymentsComparisonResponse.from(payments.compare(null, from, to));
    }

    @GetMapping("/works/{workId}/payments/comparison")
    @Operation(operationId = "compareWorkCashPayments", summary = "Compares recorded payments for one work with the immediately preceding period",
            description = "Requires PAYABLES.READ and WORKS.READ. Inclusive effective dates through today UTC. Includes WORK titles for this work, including those without a contract.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public CashPaymentsComparisonResponse work(@PathVariable UUID workId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CashPaymentsComparisonResponse.from(payments.compare(workId, from, to));
    }

    @GetMapping("/payments/comparison/by-category")
    @Operation(operationId = "compareCompanyCashPaymentsByCategory", summary = "Compares recorded company payments by category",
            description = "Requires PAYABLES.READ and COSTS_EXPENSES.READ. Uses inclusive effective dates through today UTC and the preceding window of equal length. Includes inactive categories and an uncategorized group. Separates currencies and WORK/COMPANY.")
    public CashPaymentsByCategoryResponse companyByCategory(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CashPaymentsByCategoryResponse.from(payments.compareByCategory(null, from, to));
    }

    @GetMapping("/works/{workId}/payments/comparison/by-category")
    @Operation(operationId = "compareWorkCashPaymentsByCategory", summary = "Compares recorded work payments by category",
            description = "Requires PAYABLES.READ, COSTS_EXPENSES.READ and WORKS.READ. Includes WORK payments for this work, including titles without a contract or category. Uses the preceding window of equal length and separates currencies.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public CashPaymentsByCategoryResponse workByCategory(@PathVariable UUID workId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return CashPaymentsByCategoryResponse.from(payments.compareByCategory(workId, from, to));
    }
}
