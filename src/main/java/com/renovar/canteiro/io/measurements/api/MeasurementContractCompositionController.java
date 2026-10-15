package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.request.ComposeMeasurementServiceRequest;
import com.renovar.canteiro.io.measurements.api.response.MeasurementContractCompositionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementServiceGroupResponse;
import com.renovar.canteiro.io.measurements.application.ComposeMeasurementServiceCommand;
import com.renovar.canteiro.io.measurements.application.MeasurementContractCompositionResult;
import com.renovar.canteiro.io.measurements.application.MeasurementContractCompositionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementContractCompositionController {
    private final MeasurementContractCompositionService service;

    @GetMapping("/{measurementId}/versions/{versionId}/services")
    @Operation(summary = "Lists stable service groups in a measurement version")
    @ApiResponses({@ApiResponse(responseCode = "200", description = "Measurement service groups"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")})
    public List<MeasurementServiceGroupResponse> list(@PathVariable UUID measurementId, @PathVariable UUID versionId) {
        return service.listServices(measurementId, versionId).stream().map(MeasurementServiceGroupResponse::from).toList();
    }

    @PostMapping("/{measurementId}/versions/{versionId}/services/{measurementServiceId}/contract-compositions")
    @Operation(summary = "Applies an accepted measurement service once to a compatible contract service")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Composition applied", content = @Content(schema = @Schema(implementation = MeasurementContractCompositionResponse.class))),
            @ApiResponse(responseCode = "200", description = "Existing idempotent composition returned", content = @Content(schema = @Schema(implementation = MeasurementContractCompositionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Composition submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementContractCompositionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<MeasurementContractCompositionResponse> compose(@PathVariable UUID measurementId, @PathVariable UUID versionId,
                                                                            @PathVariable UUID measurementServiceId,
                                                                            @Valid @RequestBody ComposeMeasurementServiceRequest request) {
        MeasurementContractCompositionResult result = service.compose(new ComposeMeasurementServiceCommand(measurementId, versionId,
                measurementServiceId, request.action(), request.contractId(), request.contractServiceId(), request.justification()));
        if (result.changeRequest() != null) return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(MeasurementContractCompositionResponse.requested(result.changeRequest().getId(), result.authorizationMode()));
        return ResponseEntity.status(result.alreadyComposed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(MeasurementContractCompositionResponse.direct(result.composition(), result.authorizationMode(), result.alreadyComposed(), service.currency(result.composition().getContractId())));
    }
}
