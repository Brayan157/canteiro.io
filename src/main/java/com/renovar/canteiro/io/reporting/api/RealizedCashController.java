package com.renovar.canteiro.io.reporting.api;

import com.renovar.canteiro.io.reporting.application.ReadRealizedCashUseCase;
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
public class RealizedCashController {
    private final ReadRealizedCashUseCase cash;

    @GetMapping("/realized")
    @Operation(operationId = "getCompanyRealizedCash", summary = "Reads company cash movements by currency",
            description = "Inclusive effective dates, through today UTC. RECEIVABLES.READ enables receipts; PAYABLES.READ enables payments. At least one is required. Company and work payments are shown separately. No bank opening balance or unrecorded activity is assumed.")
    public RealizedCashResponse company(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return RealizedCashResponse.from(cash.read(null, from, to));
    }

    @GetMapping("/works/{workId}/realized")
    @Operation(operationId = "getWorkRealizedCash", summary = "Reads cash movements of one work by currency",
            description = "Inclusive effective dates, through today UTC. Requires WORKS.READ and at least one of RECEIVABLES.READ and PAYABLES.READ. Includes receipts of the work contracts and payments linked to the work, including payables without a contract.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public RealizedCashResponse work(@PathVariable UUID workId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return RealizedCashResponse.from(cash.read(workId, from, to));
    }

    @GetMapping("/works/{workId}/realized/accumulated")
    @Operation(operationId = "getWorkAccumulatedRealizedCash", summary = "Reads accumulated recorded cash for one work",
            description = "Requires WORKS.READ and at least one of RECEIVABLES.READ and PAYABLES.READ. "
                    + "Covers the first authorized effective movement across currencies through today UTC, "
                    + "including movements before work registration or after completion. The start is null without movements.")
    @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    public RealizedCashResponse accumulatedWork(@PathVariable UUID workId) {
        return RealizedCashResponse.from(cash.readAccumulatedWork(workId));
    }
}
