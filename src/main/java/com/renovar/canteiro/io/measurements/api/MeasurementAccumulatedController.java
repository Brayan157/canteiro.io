package com.renovar.canteiro.io.measurements.api;
import com.renovar.canteiro.io.measurements.api.response.MeasurementAccumulatedResponse;
import com.renovar.canteiro.io.measurements.application.GetAccumulatedMeasurementUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/company/measurements") @RequiredArgsConstructor @Tag(name="Measurements")
public class MeasurementAccumulatedController {
    private final GetAccumulatedMeasurementUseCase accumulated;
    @GetMapping("/{measurementId}/accumulated")
    @Operation(summary="Returns accepted history and the current advance grouped by continuous service",
        description="Requires MEASUREMENTS.READ. Includes accepted activities and the current advance under their stable continuousServiceId, even when the revision is empty. Proven migration artifacts retired without deletion are excluded; intentionally empty services remain. Only current DRAFT items are editable. Monetary totals are official decimal strings; finance is explicitly unavailable without its permissions or contract.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Official accumulated view",useReturnTypeSchema=true),
        @ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),@ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail")})
    public MeasurementAccumulatedResponse get(@PathVariable UUID measurementId){return accumulated.get(measurementId);}
}

