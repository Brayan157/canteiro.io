package com.renovar.canteiro.io.contracts.api;

import com.renovar.canteiro.io.contracts.api.request.CreateContractRequest;
import com.renovar.canteiro.io.contracts.api.request.SetContractDiscountRequest;
import com.renovar.canteiro.io.contracts.api.request.ReverseDiscountRequest;
import com.renovar.canteiro.io.contracts.api.request.UpsertContractServiceRequest;
import com.renovar.canteiro.io.contracts.api.request.UpdateContractRequest;
import com.renovar.canteiro.io.contracts.api.response.ContractChangeResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractFinancialTotalsResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractServiceChangeResponse;
import com.renovar.canteiro.io.contracts.api.response.ContractServiceResponse;
import com.renovar.canteiro.io.contracts.application.ContractManagementService;
import com.renovar.canteiro.io.contracts.application.ContractServiceChangeResult;
import com.renovar.canteiro.io.contracts.application.ContractServiceCommand;
import com.renovar.canteiro.io.contracts.application.ContractServiceManagementService;
import com.renovar.canteiro.io.contracts.application.CreateContractCommand;
import com.renovar.canteiro.io.contracts.application.ContractFilter;
import com.renovar.canteiro.io.contracts.application.UpdateContractCommand;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.math.BigDecimal;
import java.math.RoundingMode;

@RestController
@RequestMapping("/api/v1/company/contracts")
@RequiredArgsConstructor
@Tag(name = "Contracts")
public class ContractController {

    private final ContractManagementService contractManagementService;
    private final ContractServiceManagementService contractServiceManagementService;
    private final com.renovar.canteiro.io.contracts.application.GetContractClosureEligibilityUseCase closureEligibility;
    private final com.renovar.canteiro.io.contracts.application.GetContractDetailsUseCase contractDetails;

    private static final Set<String> SORT_FIELDS = Set.of(
            "name", "reference", "status", "startedOn", "expectedCompletionOn", "completedOn", "createdAt", "updatedAt",
            "openFirst"
    );

    @GetMapping
    @Operation(summary = "Lists contracts in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paginated contract list", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<ContractResponse> findAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @io.swagger.v3.oas.annotations.Parameter(description = "Repeatable field,asc or field,desc. Fields: "
                    + "name, reference, status, startedOn, expectedCompletionOn, completedOn, createdAt, updatedAt, openFirst. "
                    + "openFirst,asc puts OPEN and ACTIVE first before pagination; desc reverses those groups. "
                    + "Within each group, explicit sorts apply first, followed by createdAt descending and id ascending.")
            @RequestParam(required = false) List<String> sort,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID workId,
            @RequestParam(required = false) String workName,
            @RequestParam(required = false) String finalCustomerName,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) java.time.LocalDate startedFrom,
            @RequestParam(required = false) java.time.LocalDate startedTo,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionFrom,
            @RequestParam(required = false) java.time.LocalDate expectedCompletionTo,
            @RequestParam(required = false) java.time.LocalDate completedFrom,
            @RequestParam(required = false) java.time.LocalDate completedTo
    ) {
        return PageResponse.from(
                contractManagementService.findAll(new ContractFilter(name, reference, workId, workName, finalCustomerName,
                                status, startedFrom, startedTo,
                                expectedCompletionFrom, expectedCompletionTo, completedFrom, completedTo),
                        new PageQuery(page, size, sort).toPageable(SORT_FIELDS)),
                this::toResponse
        );
    }

    @PostMapping
    @Operation(summary = "Creates a contract without requiring services",
            description = "A duplicate reference within the work returns 409 with reason CONTRACT_REFERENCE_ALREADY_EXISTS and a reference field violation. Approval revalidates the reference.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contract created", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract creation submitted for approval", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractChangeResponse> create(@Valid @RequestBody CreateContractRequest request) {
        var result = contractManagementService.create(new CreateContractCommand(
                request.workId(), request.contractIdentifier(), request.pricingMode(), request.reference(), request.name(), request.status(), request.startedOn(),
                request.expectedCompletionOn(), request.completedOn(), request.justification(), request.currency()
        ));
        ContractResponse contract = result.contract() == null ? null : toResponse(result.contract());
        return ResponseEntity.status(result.contract() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new ContractChangeResponse(
                        contract,
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Finds a contract in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract found", content = @Content(schema = @Schema(implementation = ContractResponse.class))),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ContractResponse find(@PathVariable UUID contractId) {
        var details = contractDetails.find(contractId);
        return toResponse(details.contract(), details.hasActiveContractDiscount());
    }

    @PutMapping("/{contractId}")
    @Operation(summary = "Updates a contract while preserving its work relationship", description = "Lifecycle guards also apply here: completion requires all services terminal and eligible; cancellation requires CANCEL authorization and all services cancelled without live commitments. Prefer the dedicated status route to avoid profile snapshots. 202 is only a proposal; approval revalidates. A changed reference already used in the work returns 409 with reason CONTRACT_REFERENCE_ALREADY_EXISTS and a reference field violation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract updated", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "202", description = "Contract update submitted for approval", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractChangeResponse> update(@PathVariable UUID contractId,
                                                          @Valid @RequestBody UpdateContractRequest request) {
        var result = contractManagementService.update(contractId, new UpdateContractCommand(request.contractIdentifier(), request.reference(), request.name(),
                request.status(), request.startedOn(), request.expectedCompletionOn(), request.completedOn(), request.justification(), request.currency()));
        ContractResponse contract = result.contract() == null ? null : toResponse(result.contract());
        return ResponseEntity.status(contract == null ? HttpStatus.ACCEPTED : HttpStatus.OK).body(new ContractChangeResponse(contract,
                result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PutMapping("/{contractId}/status")
    @Operation(summary = "Changes only the contract status and optional completion date",
            description = "Reads current profile under the contract lock; never requires client financial/profile snapshots. UPDATE authorization for completion/reopening, CANCEL for cancellation. Same lifecycle decisions as closure-eligibility; invalid transition gives409 with controlled reason. 202 stores only status intent, no official change; approval rereads current profile and revalidates. completedOn omitted/null preserves its current value, never automatically cleared or invented.")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = ContractChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<ContractChangeResponse> updateStatus(@PathVariable UUID contractId,
            @Valid @RequestBody com.renovar.canteiro.io.contracts.api.request.UpdateContractStatusRequest request) {
        var result = contractManagementService.updateStatus(contractId, request.status(), request.completedOn(), request.justification());
        if (result.changeRequest() != null) return ResponseEntity.accepted().body(new ContractChangeResponse(null,result.changeRequest().getId(),result.mode()));
        return ResponseEntity.ok(new ContractChangeResponse(toResponse(result.contract()),null,result.mode()));
    }

    @PutMapping("/{contractId}/services/{serviceId}/status")
    @Operation(summary = "Changes only the contract service status",
            description = "Reads current name, price, quantity and discounts under lock. UPDATE for completion/reactivation, CANCEL for cancellation. Completion requires exact proven settlement of the official commercial net value, including registered item withholding; credit/unknown legacy require reconciliation. No cascade or inferred receipt. 202 stores only status intent; approval preserves the profile current at application. Same controlled409 reasons as closure-eligibility.")
    @ApiResponses({@ApiResponse(responseCode = "200", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<ContractServiceChangeResponse> updateServiceStatus(@PathVariable UUID contractId, @PathVariable UUID serviceId,
            @Valid @RequestBody com.renovar.canteiro.io.contracts.api.request.UpdateContractServiceStatusRequest request) {
        return serviceResponse(contractServiceManagementService.updateStatus(contractId,serviceId,request.status(),request.justification()), HttpStatus.OK);
    }

    @GetMapping("/{contractId}/closure-eligibility")
    @Operation(summary = "Gets official contract and service lifecycle eligibility",
            description = "Requires CONTRACTS.READ and SERVICES.READ, session tenant only. Summary considers ALL services before optional serviceId filtering and paging. Fixed service order name,id ascending; page>=0,size1..100. serviceId must belong to this contract and tenant. Financial decisions require BILLING.READ, RECEIVABLES.READ and INVOICES.READ; otherwise financial eligibility is null with CLOSURE_FINANCIAL_READ_REQUIRED, never fabricated false/zero. No amounts, financial IDs or private history are exposed. Completion uses UPDATE authorization; cancellation uses CANCEL, with effective DIRECT or REQUEST_APPROVAL. DRAFT/ACTIVE services prevent contract completion; cancellation requires all services CANCELLED and no live financial commitments. Empty contracts are allowed. COMPLETED/FINALIZED share the completion guard. Services require exact cash plus proven withholding covering their official net commercial value; credit and unproven legacy require reconciliation, zero value without commitments can complete. No mutation/reservation; existing PUT/approval revalidate under locks and preserve discounts/history. Closed contracts must be explicitly reopened before creating/reactivating services or adding measurement composition.")
    @ApiResponses({@ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<com.renovar.canteiro.io.contracts.api.response.ContractClosureEligibilityResponse> closureEligibility(@PathVariable UUID contractId,
            @RequestParam(required = false) UUID serviceId, @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(com.renovar.canteiro.io.contracts.api.response.ContractClosureEligibilityResponse.from(
                        closureEligibility.get(contractId, serviceId, new PageQuery(page, size, null).toPageable(Set.of()))));
    }

    @GetMapping("/{contractId}/services")
    @Operation(summary = "Lists services belonging to a contract in the authenticated company")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract services", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public List<ContractServiceResponse> findServices(@PathVariable UUID contractId) {
        return contractManagementService.findServices(contractId).stream()
                .map(this::toServiceResponse)
                .toList();
    }

    @PostMapping("/{contractId}/services")
    @Operation(summary = "Creates a service for a contract directly or submits it for approval")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Service created", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service creation submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> createService(@PathVariable UUID contractId,
                                                                         @Valid @RequestBody UpsertContractServiceRequest request) {
        return serviceResponse(contractServiceManagementService.create(contractId, command(request)), HttpStatus.CREATED);
    }

    @PutMapping("/{contractId}/services/{serviceId}")
    @Operation(summary = "Updates a service without changing its contract relationship",
            description = "Completion requires exact proven financial settlement; cancellation requires CANCEL authorization and no live commitments, with reason codes matching closure-eligibility. Prefer the status-only route. Operational updates, including COMPLETED status, preserve the current service discount when discountType/discountValue are omitted or both null. Approval preserves the discount current at application time. An explicit equal pair is accepted as an echo; a different pair returns409 Problem Details with reason SERVICE_DISCOUNT_CHANGE_REQUIRES_DEDICATED_OPERATION. Use dedicated DISCOUNTS operations for changes/reversals. No implicit reversal, tenant from session, existing billing limits and approval apply.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service update submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> updateService(@PathVariable UUID contractId, @PathVariable UUID serviceId,
                                                                         @Valid @RequestBody UpsertContractServiceRequest request) {
        return serviceResponse(contractServiceManagementService.update(contractId, serviceId, command(request)), HttpStatus.OK);
    }

    @PutMapping("/{contractId}/services/{serviceId}/discount")
    @Operation(summary = "Applies or changes the discount of one contract service", description = "New discount is blocked by an official open charge of this contract even without an invoice (CONTRACT_HAS_OPEN_CHARGES). Contract net must cover confirmed cash net of reversals (CONTRACT_NET_BELOW_RECEIVED_AMOUNT), plus all existing approved billing/base/discount commitments. Checks precede 202 and repeat at approval under locks; never count withholding or credit as cash.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service discount updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service discount submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> setServiceDiscount(@PathVariable UUID contractId, @PathVariable UUID serviceId,
                                                                              @Valid @RequestBody SetContractDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.setServiceDiscount(contractId, serviceId, request.discountType(),
                decimal(request.discountValue()), request.justification()), HttpStatus.OK);
    }

    @PostMapping("/{contractId}/services/{serviceId}/discount/reversal")
    @Operation(summary = "Reverses the active service discount without deleting its audit history")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Service discount reversed", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Service discount reversal submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> reverseServiceDiscount(@PathVariable UUID contractId, @PathVariable UUID serviceId,
                                                                                  @Valid @RequestBody ReverseDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.reverseServiceDiscount(contractId, serviceId, request.justification()), HttpStatus.OK);
    }

    @PutMapping("/{contractId}/discount")
    @Operation(summary = "Applies or changes the contract-level adjustment without altering its services", description = "CONTRACT_HAS_OPEN_CHARGES blocks discount while any non-cancelled official title has positive outstanding balance, even without an invoice; raw installment OPEN is not the source. CONTRACT_NET_BELOW_RECEIVED_AMOUNT protects confirmed cash net of reversals. Approved billing, origin capacity and discount commitments remain additional stricter limits. Validated before 202 and at approval under locks; no financial effect until applied.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract adjustment updated", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract adjustment submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> setContractDiscount(@PathVariable UUID contractId,
                                                                               @Valid @RequestBody SetContractDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.setContractDiscount(contractId, request.discountType(),
                decimal(request.discountValue()), request.justification()), HttpStatus.OK);
    }

    @PostMapping("/{contractId}/discount/reversal")
    @Operation(summary = "Reverses the active contract adjustment without physical deletion")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract discount reversed", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "202", description = "Contract discount reversal submitted for approval", content = @Content(schema = @Schema(implementation = ContractServiceChangeResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<ContractServiceChangeResponse> reverseContractDiscount(@PathVariable UUID contractId,
                                                                                   @Valid @RequestBody ReverseDiscountRequest request) {
        return serviceResponse(contractServiceManagementService.reverseContractDiscount(contractId, request.justification()), HttpStatus.OK);
    }

    private ResponseEntity<ContractServiceChangeResponse> serviceResponse(ContractServiceChangeResult result, HttpStatus directStatus) {
        ContractServiceChangeResponse response = new ContractServiceChangeResponse(result.service() == null ? null : toServiceResponse(result.service()),
                result.contractNetAmount() == null ? null : new ContractFinancialTotalsResponse(money(result.contractNetAmount().serviceSubtotal()),
                        money(result.contractNetAmount().contractDiscountAmount()), money(result.contractNetAmount().netAmount())),
                result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode());
        return ResponseEntity.status(result.service() == null && result.contractNetAmount() == null ? HttpStatus.ACCEPTED : directStatus).body(response);
    }

    private ContractServiceCommand command(UpsertContractServiceRequest request) {
        return new ContractServiceCommand(request.sourceServiceTemplateId(), request.name(), request.description(), request.status(), decimal(request.quantity()),
                decimal(request.unitPrice()), request.discountType(), request.discountValue() == null ? null : decimal(request.discountValue()),
                request.justification());
    }

    private ContractServiceResponse toServiceResponse(com.renovar.canteiro.io.contracts.domain.ContractService service) {
        return new ContractServiceResponse(service.getId(), service.getContractId(), service.getSourceServiceTemplateId(), service.getName(),
                service.getDescription(), service.getStatus(), quantity(service.getQuantity()), money(service.getUnitPrice()), money(service.getGrossAmount()),
                service.getDiscountType(), service.getDiscountValue() == null ? null : decimalString(service.getDiscountValue(), 4),
                money(service.getDiscountAmount()), money(service.getNetAmount()), money(service.getMeasurementCompositionAmount()),
                money(service.getEffectiveNetAmount()));
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }
    private String quantity(BigDecimal value) { return decimalString(value, 4); }
    private String money(BigDecimal value) { return decimalString(value, 2); }
    private String decimalString(BigDecimal value, int scale) { return value.setScale(scale, RoundingMode.HALF_UP).toPlainString(); }

    private ContractResponse toResponse(Contract contract) {
        return toResponse(contract, null);
    }

    private ContractResponse toResponse(Contract contract, Boolean hasActiveDiscount) {
        return new ContractResponse(
                contract.getId(), contract.getWorkId(), contract.getContractIdentifier(), contract.getPricingMode(), contract.getReference(), contract.getName(), contract.getStatus(),
                contract.getStartedOn(), contract.getExpectedCompletionOn(), contract.getCompletedOn(), contract.getCurrency(), hasActiveDiscount
        );
    }
}
