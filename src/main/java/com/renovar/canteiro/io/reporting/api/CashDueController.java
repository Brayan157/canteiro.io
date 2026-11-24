package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.ReadCashDueUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
public class CashDueController {
    private final ReadCashDueUseCase due;

    @GetMapping("/due")
    @Operation(operationId = "getCompanyCashDue", summary = "Reads overdue and upcoming official company balances",
            description = "Current snapshot at today's UTC cutoff. The upcoming window includes today. RECEIVABLES.READ and PAYABLES.READ independently enable their corresponding balances. At least one is required.")
    public CashDueResponse company(
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "365", defaultValue = "30"))
            @RequestParam(defaultValue = "" + ReadCashDueUseCase.DEFAULT_HORIZON_DAYS) int horizonDays) {
        return CashDueResponse.from(due.read(null, horizonDays));
    }

    @GetMapping("/works/{workId}/due")
    @Operation(operationId = "getWorkCashDue", summary = "Reads overdue and upcoming official balances of a work",
            description = "Requires WORKS.READ and at least one financial read permission. Current snapshot at today's UTC cutoff. Includes receivables of the work contracts and WORK payables, including payables without a contract.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public CashDueResponse work(@PathVariable UUID workId,
            @Parameter(schema = @Schema(type = "integer", format = "int32", minimum = "1", maximum = "365", defaultValue = "30"))
            @RequestParam(defaultValue = "" + ReadCashDueUseCase.DEFAULT_HORIZON_DAYS) int horizonDays) {
        return CashDueResponse.from(due.read(workId, horizonDays));
    }
}
