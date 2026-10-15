package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.response.MeasurementFinancialSummaryResponse;
import com.renovar.canteiro.io.measurements.application.GetMeasurementFinancialSummaryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementFinancialSummaryController {

    private final GetMeasurementFinancialSummaryUseCase useCase;

    @GetMapping("/{measurementId}/financial-summary")
    @Operation(summary = "Returns official accepted, billed and received amounts by measurement origin",
            description = "Requires MEASUREMENTS.READ, BILLING.READ and RECEIVABLES.READ. Only accepted origins participate; "
                    + "draft, sent and pending versions never enter this projection.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official measurement financial summary",
                    content = @Content(schema = @Schema(implementation = MeasurementFinancialSummaryResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public MeasurementFinancialSummaryResponse get(@PathVariable UUID measurementId) {
        return MeasurementFinancialSummaryResponse.from(useCase.get(measurementId));
    }
}
