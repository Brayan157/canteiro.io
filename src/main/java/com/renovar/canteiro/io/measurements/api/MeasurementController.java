package com.renovar.canteiro.io.measurements.api;

import com.renovar.canteiro.io.measurements.api.request.AdvanceMeasurementWorkflowRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementDiscountRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementItemRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementRequest;
import com.renovar.canteiro.io.measurements.api.request.CreateMeasurementRevisionRequest;
import com.renovar.canteiro.io.measurements.api.request.LinkMeasurementRequest;
import com.renovar.canteiro.io.measurements.api.request.InsertMeasurementItemRequest;
import com.renovar.canteiro.io.measurements.api.request.ReorderMeasurementItemsRequest;
import com.renovar.canteiro.io.measurements.api.request.UpdateMeasurementItemRequest;
import com.renovar.canteiro.io.measurements.api.request.CorrectAcceptedMeasurementItemRequest;
import com.renovar.canteiro.io.measurements.api.response.MeasurementChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementCancellationResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementDetailsResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementDiscountChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemChangeResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemCorrectionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementItemReorderResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementSpreadsheetImportApplyResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementSpreadsheetImportPreviewResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementRevisionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementVersionResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementVersionDetailsResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementVersionHistoryResponse;
import com.renovar.canteiro.io.measurements.api.response.MeasurementWorkflowResponse;
import com.renovar.canteiro.io.measurements.application.AdvanceMeasurementWorkflowCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementDiscountCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.CreateMeasurementRevisionCommand;
import com.renovar.canteiro.io.measurements.application.MeasurementCreationResult;
import com.renovar.canteiro.io.measurements.application.MeasurementCancellationResult;
import com.renovar.canteiro.io.measurements.application.MeasurementCancellationService;
import com.renovar.canteiro.io.measurements.application.MeasurementDiscountChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementDiscountService;
import com.renovar.canteiro.io.measurements.application.MeasurementItemChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemCorrectionResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemCorrectionService;
import com.renovar.canteiro.io.measurements.application.MeasurementItemInsertionResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemInsertionService;
import com.renovar.canteiro.io.measurements.application.MeasurementItemReorderResult;
import com.renovar.canteiro.io.measurements.application.MeasurementItemReorderService;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportResult;
import com.renovar.canteiro.io.measurements.application.MeasurementSpreadsheetImportService;
import com.renovar.canteiro.io.measurements.application.InsertMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.ReorderMeasurementItemsCommand;
import com.renovar.canteiro.io.measurements.application.UpdateMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.CorrectAcceptedMeasurementItemCommand;
import com.renovar.canteiro.io.measurements.application.LinkMeasurementCommand;
import com.renovar.canteiro.io.measurements.application.MeasurementLinkResult;
import com.renovar.canteiro.io.measurements.application.MeasurementLinkService;
import com.renovar.canteiro.io.measurements.application.MeasurementManagementService;
import com.renovar.canteiro.io.measurements.application.MeasurementFilter;
import com.renovar.canteiro.io.measurements.application.MeasurementRevisionChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementRevisionService;
import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowChangeResult;
import com.renovar.canteiro.io.measurements.application.MeasurementWorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;

import java.math.BigDecimal;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/measurements")
@RequiredArgsConstructor
@Tag(name = "Measurements")
public class MeasurementController {

    private static final Set<String> SORT_FIELDS = Set.of("reference", "measuredOn", "status", "createdAt", "updatedAt");
    private static final List<String> DEFAULT_SORT = List.of("measuredOn,desc");

    private final MeasurementManagementService managementService;
    private final MeasurementDiscountService discountService;
    private final MeasurementWorkflowService workflowService;
    private final MeasurementRevisionService revisionService;
    private final MeasurementLinkService linkService;
    private final MeasurementCancellationService cancellationService;
    private final MeasurementItemCorrectionService itemCorrectionService;
    private final MeasurementItemInsertionService itemInsertionService;
    private final MeasurementItemReorderService itemReorderService;
    private final MeasurementSpreadsheetImportService spreadsheetImportService;

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Lists measurements in the authenticated company",
            description = "Requires MEASUREMENTS.READ. Allowed filters: workId, contractId, reference, status, measuredOnFrom and measuredOnTo. "
                    + "Allowed sort fields: measuredOn, reference, status, createdAt and updatedAt. Default: measuredOn,desc."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated measurement list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<MeasurementResponse> findAll(
            @Parameter(description = "Zero-based page index. Must be greater than or equal to zero.")
            @RequestParam(required = false) Integer page,
            @Parameter(description = "Page size from 1 to 100.")
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed values: measuredOn, reference, status, createdAt or updatedAt, followed by asc or desc. Default: measuredOn,desc.")
            @RequestParam(required = false) List<String> sort,
            @Parameter(description = "Exact work identifier in the authenticated company.")
            @RequestParam(required = false) UUID workId,
            @Parameter(description = "Exact contract identifier in the authenticated company.")
            @RequestParam(required = false) UUID contractId,
            @Parameter(description = "Case-insensitive partial match against the measurement reference.")
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) com.renovar.canteiro.io.measurements.domain.MeasurementStatus status,
            @Parameter(description = "Inclusive first measuredOn date, in ISO-8601 format.")
            @RequestParam(required = false) LocalDate measuredOnFrom,
            @Parameter(description = "Inclusive last measuredOn date, in ISO-8601 format.")
            @RequestParam(required = false) LocalDate measuredOnTo
    ) {
        List<String> resolvedSort = sort == null || sort.isEmpty() ? DEFAULT_SORT : sort;
        return PageResponse.from(managementService.findAll(
                        new MeasurementFilter(workId, contractId, reference, status, measuredOnFrom, measuredOnTo),
                        new PageQuery(page, size, resolvedSort).toPageable(SORT_FIELDS)),
                MeasurementResponse::from);
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Creates a measurement and its initial draft version",
            description = "Currency is immutable. Standalone defaults to BRL; linked creation derives the contract currency when omitted and returns 409 if supplied differently. No currency conversion.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Measurement created", content = @Content(schema = @Schema(implementation = MeasurementChangeResponse.class))),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "202", description = "Measurement creation submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementChangeResponse> create(@Valid @RequestBody CreateMeasurementRequest request) {
        MeasurementCreationResult result = managementService.create(new CreateMeasurementCommand(request.workId(),
                request.contractId(), request.title(), request.reference(), request.description(), request.measuredOn(),
                request.justification(), request.currency()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementChangeResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.version()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PutMapping(value = "/{measurementId}/link", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Links a measurement to a work and, optionally, a contract",
            description = "Requires MEASUREMENTS.UPDATE. The work is mandatory for this operation and must belong to "
                    + "the authenticated company. The contract is optional, but when supplied it must belong to the "
                    + "selected work. An existing work or contract link cannot be moved to another record."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Measurement linked directly", content = @Content(schema = @Schema(implementation = MeasurementChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Link submitted for approval; no official measurement is changed", content = @Content(schema = @Schema(implementation = MeasurementChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementChangeResponse> link(
            @PathVariable UUID measurementId,
            @Valid @RequestBody LinkMeasurementRequest request
    ) {
        MeasurementLinkResult result = linkService.link(new LinkMeasurementCommand(
                measurementId, request.workId(), request.contractId(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new MeasurementChangeResponse(MeasurementResponse.from(result.measurement()), null,
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @DeleteMapping(value = "/{measurementId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Logically deletes a measurement without a contract",
            description = "Requires MEASUREMENTS.CANCEL. No row, version, item, document or audit event is physically "
                    + "deleted. A measurement linked to a contract cannot be cancelled and returns 409."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Measurement cancelled without physical deletion"),
            @ApiResponse(responseCode = "202", description = "Cancellation submitted for approval; official status remains unchanged", content = @Content(schema = @Schema(implementation = MeasurementCancellationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<?> cancel(
            @PathVariable UUID measurementId,
            @Size(max = 2000) @RequestParam(required = false) String justification
    ) {
        MeasurementCancellationResult result = cancellationService.cancel(measurementId, justification);
        if (result.changeRequest() == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.accepted().body(new MeasurementCancellationResponse(
                result.changeRequest().getId(), result.authorizationMode()));
    }

    @GetMapping(value = "/{measurementId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns a measurement with its latest version, items, discount and totals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Measurement details", content = @Content(schema = @Schema(implementation = MeasurementDetailsResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public MeasurementDetailsResponse find(@PathVariable UUID measurementId) {
        return MeasurementDetailsResponse.from(managementService.find(measurementId));
    }

    @GetMapping(value = "/{measurementId}/versions", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Lists the measurement version timeline with official totals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versions ordered by version number descending",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = MeasurementVersionHistoryResponse.class)))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<MeasurementVersionHistoryResponse> findVersionHistory(@PathVariable UUID measurementId) {
        return managementService.findVersionHistory(measurementId).stream()
                .map(MeasurementVersionHistoryResponse::from)
                .toList();
    }

    @GetMapping(value = "/{measurementId}/versions/{versionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Returns one selected measurement version with its official items and totals")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Measurement version details",
                    content = @Content(schema = @Schema(implementation = MeasurementVersionDetailsResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public MeasurementVersionDetailsResponse findVersion(@PathVariable UUID measurementId,
                                                          @PathVariable UUID versionId) {
        return MeasurementVersionDetailsResponse.from(managementService.findVersion(measurementId, versionId));
    }

    @PostMapping(value = "/{measurementId}/versions/{versionId}/items", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Adds a calculated item to a draft measurement version")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Measurement item created", content = @Content(schema = @Schema(implementation = MeasurementItemChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Measurement item creation submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementItemChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemChangeResponse> addItem(
            @PathVariable UUID measurementId,
            @PathVariable UUID versionId,
            @Valid @RequestBody CreateMeasurementItemRequest request
    ) {
        MeasurementItemChangeResult result = managementService.addItem(new CreateMeasurementItemCommand(measurementId,
                versionId, request.itemNumber(), request.serviceTitle(), request.activity(), request.description(),
                request.measuredOn(), request.chargeType(),
                request.areaSquareMeters(), request.linearMeters(), request.kilogramsPerSquareMeter(),
                request.kilogramsPerLinearMeter(), request.unitQuantity(), new BigDecimal(request.unitPrice()),
                decimal(request.previewTotalWeightKg()), decimal(request.previewTotalAmount()), request.justification(), request.continuousServiceId()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementItemChangeResponse(MeasurementItemResponse.from(result.item()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PostMapping(value = "/{measurementId}/versions/{versionId}/items/insert", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Inserts a calculated item after a draft measurement item",
            description = "Atomically inserts a line after insertAfterItemId and renumbers only active lines. "
                    + "A pending approval never changes the official order or totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Item inserted and active order recalculated", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Insertion submitted for approval without changing official order", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemCorrectionResponse> insertItem(
            @PathVariable UUID measurementId, @PathVariable UUID versionId,
            @Valid @RequestBody InsertMeasurementItemRequest request) {
        var item = request.item();
        MeasurementItemInsertionResult result = itemInsertionService.insert(new InsertMeasurementItemCommand(
                measurementId, versionId, request.insertAfterItemId(), item.serviceTitle(), item.activity(), item.description(),
                item.measuredOn(), item.chargeType(), item.areaSquareMeters(), item.linearMeters(),
                item.kilogramsPerSquareMeter(), item.kilogramsPerLinearMeter(), item.unitQuantity(),
                new BigDecimal(item.unitPrice()), decimal(item.previewTotalWeightKg()), decimal(item.previewTotalAmount()),
                item.justification(), item.continuousServiceId()));
        MeasurementItemCorrectionResponse body = result.changeRequest() == null
                ? MeasurementItemCorrectionResponse.direct(MeasurementItemResponse.from(result.item()),
                MeasurementVersionResponse.from(result.version()), result.amounts().totalWeightKg(), result.amounts().grossAmount(),
                result.amounts().discountAmount(), result.amounts().netAmount(), result.authorizationMode())
                : MeasurementItemCorrectionResponse.pending(result.changeRequest().getId(), result.authorizationMode());
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED).body(body);
    }

    @PutMapping(value = "/{measurementId}/versions/{versionId}/items/reorder", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Atomically reorders all active draft measurement items",
            description = "The request must contain every active item exactly once in its final order. "
                    + "It may also change each active item's serviceTitle. The server assigns itemNumber sequentially; "
                    + "a pending approval does not change official order, service grouping or totals.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active items reordered with authoritative totals", content = @Content(schema = @Schema(implementation = MeasurementItemReorderResponse.class))),
            @ApiResponse(responseCode = "202", description = "Reorder submitted for approval without changing official data", content = @Content(schema = @Schema(implementation = MeasurementItemReorderResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemReorderResponse> reorderItems(
            @PathVariable UUID measurementId, @PathVariable UUID versionId,
            @Valid @RequestBody ReorderMeasurementItemsRequest request) {
        MeasurementItemReorderResult result = itemReorderService.reorder(new ReorderMeasurementItemsCommand(
                measurementId, versionId, request.items().stream()
                .map(item -> new ReorderMeasurementItemsCommand.ReorderedMeasurementItem(item.itemId(), item.serviceTitle(), item.continuousServiceId()))
                .toList(), request.justification()));
        MeasurementItemReorderResponse body = result.changeRequest() == null
                ? MeasurementItemReorderResponse.direct(result.items().stream().map(MeasurementItemResponse::from).toList(),
                MeasurementVersionResponse.from(result.version()), result.amounts().totalWeightKg(), result.amounts().grossAmount(),
                result.amounts().discountAmount(), result.amounts().netAmount(), result.authorizationMode())
                : MeasurementItemReorderResponse.pending(result.changeRequest().getId(), result.authorizationMode());
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
    }

    @PostMapping(value = "/{measurementId}/versions/{versionId}/items/import/preview",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Validates an XLSX measurement-item synchronization without changing official data",
            description = "Requires measurement update authority. The exported hidden Item ID column is the only stable "
                    + "synchronization key; itemNumber, service title and activity are never used as keys. "
                    + "Only DRAFT versions are eligible. Accepted or finalized versions require a new revision.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Preview, line errors and authoritative totals", content = @Content(schema = @Schema(implementation = MeasurementSpreadsheetImportPreviewResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public MeasurementSpreadsheetImportPreviewResponse previewSpreadsheetImport(
            @PathVariable UUID measurementId, @PathVariable UUID versionId,
            @RequestParam("file") MultipartFile file) throws IOException {
        return MeasurementSpreadsheetImportPreviewResponse.from(spreadsheetImportService.preview(measurementId, versionId,
                file.getOriginalFilename(), spreadsheetBytes(file)));
    }

    @PostMapping(value = "/{measurementId}/versions/{versionId}/items/import/confirm",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Confirms and atomically applies a previously previewed XLSX measurement-item synchronization",
            description = "Requires the exact SHA-256 returned by preview. Direct authority applies all changes atomically; "
                    + "request authority returns 202 and leaves official items untouched until approval. Removed lines are logically inactive.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Spreadsheet synchronization applied with authoritative totals", content = @Content(schema = @Schema(implementation = MeasurementSpreadsheetImportApplyResponse.class))),
            @ApiResponse(responseCode = "202", description = "Spreadsheet synchronization submitted for approval without changing official data", content = @Content(schema = @Schema(implementation = MeasurementSpreadsheetImportApplyResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementSpreadsheetImportApplyResponse> confirmSpreadsheetImport(
            @PathVariable UUID measurementId, @PathVariable UUID versionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam @Size(min = 64, max = 64) String spreadsheetHash,
            @RequestParam(required = false) @Size(max = 2000) String justification) throws IOException {
        MeasurementSpreadsheetImportResult result = spreadsheetImportService.confirm(measurementId, versionId,
                file.getOriginalFilename(), spreadsheetBytes(file), spreadsheetHash, justification);
        MeasurementSpreadsheetImportApplyResponse body = result.changeRequest() == null
                ? MeasurementSpreadsheetImportApplyResponse.direct(result.items().stream().map(MeasurementItemResponse::from).toList(),
                MeasurementVersionResponse.from(result.version()), result.amounts().totalWeightKg(), result.amounts().grossAmount(),
                result.amounts().discountAmount(), result.amounts().netAmount(), result.authorizationMode())
                : MeasurementSpreadsheetImportApplyResponse.pending(result.changeRequest().getId(), result.authorizationMode());
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
    }

    @PutMapping("/{measurementId}/versions/{versionId}/items/{itemId}")
    @Operation(summary = "Corrects an item while its measurement version is draft")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft item corrected with official recalculated totals", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Draft correction submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemCorrectionResponse> updateDraftItem(
            @PathVariable UUID measurementId, @PathVariable UUID versionId, @PathVariable UUID itemId,
            @Valid @RequestBody UpdateMeasurementItemRequest request) {
        MeasurementItemCorrectionResult result = itemCorrectionService.updateDraft(new UpdateMeasurementItemCommand(
                measurementId, versionId, itemId, request.itemNumber(), request.serviceTitle(), request.activity(),
                request.description(), request.measuredOn(), request.chargeType(), request.areaSquareMeters(),
                request.linearMeters(), request.kilogramsPerSquareMeter(), request.kilogramsPerLinearMeter(),
                request.unitQuantity(), new BigDecimal(request.unitPrice()), decimal(request.previewTotalWeightKg()), decimal(request.previewTotalAmount()),
                request.justification()));
        return correctionResponse(result, HttpStatus.OK);
    }

    @DeleteMapping("/{measurementId}/versions/{versionId}/items/{itemId}")
    @Operation(
            summary = "Logically removes an item while its measurement version is draft",
            description = "The item remains in audit and history with active=false. It no longer contributes to "
                    + "the normal grid, official totals or exports. Only DRAFT measurement versions are eligible; "
                    + "use a corrective revision with VOID for an accepted or sent version."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Draft item logically removed with official recalculated totals", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Draft removal submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemCorrectionResponse> removeDraftItem(
            @PathVariable UUID measurementId, @PathVariable UUID versionId, @PathVariable UUID itemId,
            @Parameter(description = "Optional operational justification, with at most 2,000 characters.")
            @RequestParam(required = false) @Size(max = 2000) String justification) {
        return correctionResponse(itemCorrectionService.removeDraft(measurementId, versionId, itemId, justification), HttpStatus.OK);
    }

    @PostMapping("/{measurementId}/versions/{versionId}/items/{itemId}/corrective-revision")
    @Operation(summary = "Creates a corrective draft revision from an accepted measurement item",
            description = "The accepted item remains frozen. REPLACE copies the accepted version and replaces one line; VOID copies it and logically removes that line.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Corrective draft revision created with official totals", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Corrective revision submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementItemCorrectionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementItemCorrectionResponse> correctAcceptedItem(
            @PathVariable UUID measurementId, @PathVariable UUID versionId, @PathVariable UUID itemId,
            @Valid @RequestBody CorrectAcceptedMeasurementItemRequest request) {
        MeasurementItemCorrectionResult result = itemCorrectionService.correctAccepted(new CorrectAcceptedMeasurementItemCommand(
                measurementId, versionId, itemId, request.action(), request.itemNumber(), request.serviceTitle(),
                request.activity(), request.description(), request.measuredOn(), request.chargeType(), request.areaSquareMeters(),
                request.linearMeters(), request.kilogramsPerSquareMeter(), request.kilogramsPerLinearMeter(),
                request.unitQuantity(), decimal(request.unitPrice()), decimal(request.previewTotalWeightKg()), decimal(request.previewTotalAmount()), request.justification()));
        return correctionResponse(result, HttpStatus.CREATED);
    }

    private ResponseEntity<MeasurementItemCorrectionResponse> correctionResponse(MeasurementItemCorrectionResult result,
                                                                                   HttpStatus directStatus) {
        MeasurementItemCorrectionResponse body = result.changeRequest() == null
                ? MeasurementItemCorrectionResponse.direct(MeasurementItemResponse.from(result.item()),
                MeasurementVersionResponse.from(result.version()), result.amounts().totalWeightKg(), result.amounts().grossAmount(),
                result.amounts().discountAmount(), result.amounts().netAmount(), result.authorizationMode())
                : MeasurementItemCorrectionResponse.pending(result.changeRequest().getId(), result.authorizationMode());
        return ResponseEntity.status(result.changeRequest() == null ? directStatus : HttpStatus.ACCEPTED).body(body);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
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
    @Operation(
            summary = "Advances the measurement through sending, external acceptance and finalization",
            description = "Sending and external acceptance are available before a work link. FINALIZE requires the "
                    + "measurement to be linked to both a work and a compatible contract. When recording an external "
                    + "rejection, externalAcceptanceNotes is required and correctionItemIds may contain active items "
                    + "from this version that need correction."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Workflow advanced directly", content = @Content(schema = @Schema(implementation = MeasurementWorkflowResponse.class))),
            @ApiResponse(responseCode = "202", description = "Workflow change submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementWorkflowResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementWorkflowResponse> advanceWorkflow(
            @PathVariable UUID measurementId,
            @PathVariable UUID versionId,
            @Valid @RequestBody AdvanceMeasurementWorkflowRequest request
    ) {
        MeasurementWorkflowChangeResult result = workflowService.advance(new AdvanceMeasurementWorkflowCommand(
                measurementId, versionId, request.action(), request.externallyAccepted(),
                request.externalAcceptanceOn(), request.externalAcceptanceNotes(), request.correctionItemIds(),
                request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new MeasurementWorkflowResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.measurementVersion()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    @PostMapping("/{measurementId}/revisions")
    @Operation(summary = "Creates a new draft revision after an accepted or rejected measurement version",
            description = "A revision from a rejected version copies its active official items and remains linked to the "
                    + "rejected source. The source version stays immutable.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Incremental draft revision created", content = @Content(schema = @Schema(implementation = MeasurementRevisionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Incremental revision submitted for approval", content = @Content(schema = @Schema(implementation = MeasurementRevisionResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<MeasurementRevisionResponse> createRevision(
            @PathVariable UUID measurementId,
            @Valid @RequestBody CreateMeasurementRevisionRequest request
    ) {
        MeasurementRevisionChangeResult result = revisionService.create(new CreateMeasurementRevisionCommand(
                measurementId, request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .body(new MeasurementRevisionResponse(MeasurementResponse.from(result.measurement()),
                        MeasurementVersionResponse.from(result.measurementVersion()),
                        result.items().stream().map(MeasurementItemResponse::from).toList(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.authorizationMode()));
    }

    private byte[] spreadsheetBytes(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new com.renovar.canteiro.io.shared.api.error.ApiException(HttpStatus.BAD_REQUEST,
                    com.renovar.canteiro.io.shared.api.error.ErrorCode.MALFORMED_REQUEST, "XLSX spreadsheet file is required");
        }
        if (file.getSize() > 10L * 1024 * 1024) {
            throw new com.renovar.canteiro.io.shared.api.error.ApiException(HttpStatus.BAD_REQUEST,
                    com.renovar.canteiro.io.shared.api.error.ErrorCode.MALFORMED_REQUEST, "XLSX spreadsheet must not exceed 10 MiB");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new com.renovar.canteiro.io.shared.api.error.ApiException(HttpStatus.BAD_REQUEST,
                    com.renovar.canteiro.io.shared.api.error.ErrorCode.MALFORMED_REQUEST, "Only .xlsx measurement spreadsheets are accepted");
        }
        return file.getBytes();
    }
}
