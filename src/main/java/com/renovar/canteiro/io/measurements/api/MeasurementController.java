package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.request.AdvanceMeasurementWorkflowRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementDiscountRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementItemRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementRevisionRequest;
import com.renovar.canteiro.io.measurements.api.response.MeasurementChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementDetailsResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementDiscountChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementRevisionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementVersionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementWorkflowResponse;
import com.renovar.canteiro.io.measurements.application.AdvanceMeasurementWorkflowCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementDiscountCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementRevisionCommand;
import com.renovar.canteiro.io.measurements.application.MeasurementCreationResult;
import com.renovar.canteiro.io.measurements.application.MeasurementDiscountChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementDiscountService;
import com.renovar.canteiro.io.measurements.application.MeasurementItemChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementManagementService;
import com.renovar.canteiro.io.measurements.application.MeasurementRevisionChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementRevisionService;
import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementController {

    private final MeasurementManagementService managementService;
    private final MeasurementDiscountService discountService;
    private final MeasurementWorkflowService workflowService;
    private final MeasurementRevisionService revisionService;

    @PostMapping
    @Operation(summary = "Creates a measurement and its initial draft version")
    public ResponseEntity<MeasurementChangeResponse> create(@Valid @RequestBody CreateMeasurementRequest request) {
        MeasurementCreationResult result = managementService.create(new CreateMeasurementCommand(request.workId(),
                request.contractId(), request.reference(), request.description(), request.measuredOn(),
                request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementChangeResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.version()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @GetMapping("/{measurementId}")
    @Operation(summary = "Returns a measurement with its latest version, items, discount and totals")
    public MeasurementDetailsResponse find(@PathVariable UUID measurementId) {
        return MeasurementDetailsResponse.from(managementService.find(measurementId));
    }

    @PostMapping("/{measurementId}/versions/{versionId}/items")
    @Operation(summary = "Adds a calculated item to a draft measurement version")
    public ResponseEntity<MeasurementItemChangeResponse> addItem(
            @PathVariable UUID measurementId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateMeasurementItemRequest request
    ) {
        MeasurementItemChangeResult result = managementService.addItem(new CreateMeasurementItemCommand(measurementId,
                versionId, request.itemNumber(), request.activity(), request.description(), request.chargeType(),
                request.areaSquareMeters(), request.linearMeters(), request.kilogramsPerSquareMeter(),
                request.kilogramsPerLinearMeter(), request.unitPrice(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementItemChangeResponse(MeasurementItemResponse.from(result.item()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PostMapping("/{measurementId}/versions/{versionId}/discount")
    @Operation(summary = "Creates the separately audited header discount for a draft measurement version")
    public ResponseEntity<MeasurementDiscountChangeResponse> createDiscount(
            @PathVariable UUID measurementId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateMeasurementDiscountRequest request
    ) {
        managementService.validateVersionLink(measurementId, versionId);
        MeasurementDiscountChangeResult result = discountService.create(new CreateMeasurementDiscountCommand(versionId,
                request.discountType(), request.discountValue(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementDiscountChangeResponse(
                        result.measurementDiscount() == null ? null : result.measurementDiscount().getId(),
                        result.measurementDiscount() == null ? null : result.measurementDiscount().getDiscountType(),
                        result.measurementDiscount() == null ? null : result.measurementDiscount().getDiscountValue(),
                        result.amounts() == null ? null : result.amounts().grossAmount(),
                        result.amounts() == null ? null : result.amounts().discountAmount(),
                        result.amounts() == null ? null : result.amounts().netAmount(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PostMapping("/{measurementId}/versions/{versionId}/workflow")
    @Operation(summary = "Advances the measurement through sending, external acceptance and finalization")
    public ResponseEntity<MeasurementWorkflowResponse> advanceWorkflow(
            @PathVariable UUID measurementId,
            @PathVariable UUID versionId,
            @Valid @RequestBody AdvanceMeasurementWorkflowRequest request
    ) {
        MeasurementWorkflowChangeResult result = workflowService.advance(new AdvanceMeasurementWorkflowCommand(
                measurementId, versionId, request.action(), request.externallyAccepted(),
                request.externalAcceptanceOn(), request.externalAcceptanceNotes(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new MeasurementWorkflowResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.measurementVersion()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PostMapping("/{measurementId}/revisions")
    @Operation(summary = "Creates a new draft revision after an accepted measurement version")
    public ResponseEntity<MeasurementRevisionResponse> createRevision(
            @PathVariable UUID measurementId,
            @Valid @RequestBody CreateMeasurementRevisionRequest request
    ) {
        MeasurementRevisionChangeResult result = revisionService.create(new CreateMeasurementRevisionCommand(
                measurementId, request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementRevisionResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.measurementVersion()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }
}
