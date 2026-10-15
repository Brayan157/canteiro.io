package com.renovar.canteiro.io.measurements.api;
import com.renovar.canteiro.io.measurements.api.request.AssignMeasurementItemServiceRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementContinuousServiceRequest;
import com.renovar.canteiro.io.measurements.api.request.UpdateMeasurementServiceDestinationRequest;
import com.renovar.canteiro.io.measurements.api.response.MeasurementContinuousServiceResponse;
import com.renovar.canteiro.io.measurements.application.MeasurementCompositionProgressService;
import com.renovar.canteiro.io.measurements.application.MeasurementContinuousServiceManagementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/company/measurements") @RequiredArgsConstructor @Tag(name="Measurements")
@ApiResponses({@ApiResponse(responseCode="400",ref="#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode="403",ref="#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode="404",ref="#/components/responses/ProblemDetail"),
    @ApiResponse(responseCode="409",ref="#/components/responses/ProblemDetail")})
public class MeasurementContinuousServiceController {
    private final MeasurementContinuousServiceManagementService services;
    private final MeasurementCompositionProgressService progress;
    @GetMapping("/{measurementId}/services")
    @Operation(summary="Lists continuous service identities and persisted destinations",
        description="Preserves intentionally empty services. Excludes logically retired migration artifacts, without merging titles or changing historical item/financial origins. Reuse the returned id as continuousServiceId for activities in later advances.")
    @ApiResponse(responseCode="200",description="Continuous services",useReturnTypeSchema=true)
    public List<MeasurementContinuousServiceResponse> list(@PathVariable UUID measurementId){
        return services.list(measurementId).stream().map(MeasurementContinuousServiceResponse::from).toList();
    }
    @PostMapping("/{measurementId}/services")
    @Operation(summary="Creates a continuous service without recognizing any contract value")
    @ApiResponses({@ApiResponse(responseCode="201",description="Service created",useReturnTypeSchema=true),
        @ApiResponse(responseCode="202",description="Only a change request was created",useReturnTypeSchema=true)})
    public ResponseEntity<MeasurementContinuousServiceResponse> create(@PathVariable UUID measurementId,@Valid @RequestBody CreateMeasurementContinuousServiceRequest r){
        var result=services.create(measurementId,r.serviceTitle(),r.destinationAction()==null?null:r.destinationAction().name(),r.contractServiceId(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?201:202).body(MeasurementContinuousServiceResponse.from(result));
    }
    @PutMapping("/{measurementId}/services/{continuousServiceId}/destination")
    @Operation(summary="Persists a new/existing contract destination with optimistic concurrency")
    @ApiResponses({@ApiResponse(responseCode="200",description="Destination prepared; only already accepted authorized origins may be composed",useReturnTypeSchema=true),
        @ApiResponse(responseCode="202",description="Destination proposal only",useReturnTypeSchema=true)})
    public ResponseEntity<MeasurementContinuousServiceResponse> destination(@PathVariable UUID measurementId,@PathVariable UUID continuousServiceId,
            @Valid @RequestBody UpdateMeasurementServiceDestinationRequest r){
        var result=services.destination(measurementId,continuousServiceId,r.expectedVersion(),r.destinationAction().name(),r.contractServiceId(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?200:202).body(MeasurementContinuousServiceResponse.from(result));
    }
    @PutMapping("/{measurementId}/versions/{versionId}/items/{itemId}/service")
    @Operation(summary="Assigns an editable current-draft item to a continuous service by ID")
    @ApiResponses({@ApiResponse(responseCode="200",description="Assignment applied",useReturnTypeSchema=true),
        @ApiResponse(responseCode="202",description="Assignment proposal only",useReturnTypeSchema=true)})
    public ResponseEntity<MeasurementContinuousServiceResponse> assign(@PathVariable UUID measurementId,@PathVariable UUID versionId,
            @PathVariable UUID itemId,@Valid @RequestBody AssignMeasurementItemServiceRequest r){
        var result=services.assign(measurementId,versionId,itemId,r.continuousServiceId(),r.justification());
        return ResponseEntity.status(result.changeRequest()==null?200:202).body(MeasurementContinuousServiceResponse.from(result));
    }
    @GetMapping("/{measurementId}/contract-compositions")
    @Operation(summary="Shows external acceptance and internal composition state independently")
    @ApiResponse(responseCode="200",description="Origin composition states",useReturnTypeSchema=true)
    public List<MeasurementCompositionState> states(@PathVariable UUID measurementId){return progress.get(measurementId);}
    @PostMapping("/{measurementId}/contract-compositions/apply")
    @Operation(summary="Coordinates unapplied accepted increments using persisted destinations",
        description="Returns per-origin APPLIED/AWAITING_APPROVAL/AUTHORIZATION_REQUIRED; it never composes unaccepted items.")
    @ApiResponse(responseCode="200",description="Per-origin official and pending states",useReturnTypeSchema=true)
    public List<MeasurementCompositionState> apply(@PathVariable UUID measurementId){return progress.apply(measurementId);}
}

