package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.BillingAllocationRequest;
import com.renovar.canteiro.io.receivables.api.request.CreateApprovedBillingInstallmentRequest;
import com.renovar.canteiro.io.receivables.api.request.CancelFinancialRecordRequest;
import com.renovar.canteiro.io.receivables.api.response.BillingAllocationResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentApprovalResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableResponse;
import com.renovar.canteiro.io.receivables.api.response.FinancialCancellationResponse;
import com.renovar.canteiro.io.receivables.application.BillingAllocationCommand;
import com.renovar.canteiro.io.receivables.application.BillingInstallmentApprovalResult;
import com.renovar.canteiro.io.receivables.application.BillingInstallmentManagementService;
import com.renovar.canteiro.io.receivables.application.CreateApprovedBillingInstallmentCommand;
import com.renovar.canteiro.io.receivables.application.CancelFinancialRecordCommand;
import com.renovar.canteiro.io.receivables.application.FinancialCancellationService;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentFilter;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentStatus;
import com.renovar.canteiro.io.receivables.application.ReadBillingInstallmentsUseCase;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentSummaryResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentDetailsResponse;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/company/billing-installments")
@RequiredArgsConstructor
@Tag(name = "Billing installments")
public class BillingInstallmentController {
    private final com.renovar.canteiro.io.receivables.application.FinancialCurrencyService currencies;

    private final BillingInstallmentManagementService billingInstallmentManagementService;
    private final FinancialCancellationService financialCancellationService;
    private final ReadBillingInstallmentsUseCase readBillingInstallmentsUseCase;

    @GetMapping(produces = "application/json")
    @Operation(summary = "Lists official billing installments and their historical net amounts",
            description = "Requires BILLING.READ; company comes exclusively from the session. Cancelled history is included unless filtered. Amounts are official signed allocation totals, not remaining receivable balances; GET never rewrites statuses. With RECEIVABLES.READ, receivableFinancialStatus is the official current position of the original receivable, loaded in batch for the page; receivableReceivedWithWithholding is a presentation hint, not cash. Without permission or an original title, availability=false and both values=null. Status filters/sorting remain persisted business status, not financial status. Default order: dueOn asc, id asc, nulls last.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of official installments", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<BillingInstallmentSummaryResponse> find(
            @Parameter(description = "Optional exact contract ID in the current company. Nonmatching or foreign IDs return an empty page.") @RequestParam(required = false) UUID contractId,
            @Parameter(description = "Case-insensitive literal reference fragment, at most 100 characters; % and _ are not wildcards.") @RequestParam(required = false) String reference,
            @Parameter(description = "Exact persisted installment status; no status is derived from dates or settlements during this GET.") @RequestParam(required = false) BillingInstallmentStatus status,
            @Parameter(description = "Inclusive due date lower bound, ISO yyyy-MM-dd.") @RequestParam(required = false) LocalDate dueFrom,
            @Parameter(description = "Inclusive due date upper bound, ISO yyyy-MM-dd; must not precede dueFrom.") @RequestParam(required = false) LocalDate dueTo,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Allowed: dueOn, issuedOn, reference, status, createdAt; field,asc or field,desc. Repeat sort for multiple fields. Nulls last; ties use id asc.") @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(readBillingInstallmentsUseCase.find(new BillingInstallmentFilter(contractId, reference, status, dueFrom, dueTo),
                new PageQuery(page, size, sort).toPageable(Set.of("dueOn", "issuedOn", "reference", "status", "createdAt"))), BillingInstallmentSummaryResponse::from);
    }

    @GetMapping(value = "/{installmentId}", produces = "application/json")
    @Operation(summary = "Shows an official installment with allocations and preserved financial origins",
            description = "Requires BILLING.READ. RECEIVABLES.READ additionally permits safe receivable IDs/references; otherwise receivableReferencesAvailable=false and receivables=[]. With that permission the original-title financial status and received-with-withholding hint are also available; unavailable means false/null, never OPEN. No balances, cash amounts, settlement records, invoice data or measurement content is returned. The persisted status remains distinct and unchanged. Cancelled installments remain readable.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official installment detail", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public BillingInstallmentDetailsResponse get(@PathVariable UUID installmentId) {
        return BillingInstallmentDetailsResponse.from(readBillingInstallmentsUseCase.get(installmentId));
    }

    @PostMapping("/{installmentId}/cancellation")
    @Operation(summary = "Cancels an installment, its receivable and allocations atomically")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official operation result", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = FinancialCancellationResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<FinancialCancellationResponse> cancel(@PathVariable UUID installmentId,
                                                                 @Valid @RequestBody CancelFinancialRecordRequest request) {
        var result = financialCancellationService.cancelInstallment(installmentId,
                new CancelFinancialRecordCommand(request.reason(), request.justification()));
        return ResponseEntity.status(result.changeRequest() == null ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new FinancialCancellationResponse(result.entityId(), result.reversalId(), result.status(),
                        result.changeRequest() == null ? null : result.changeRequest().getId(), result.mode()));
    }

    @PostMapping
    @Operation(summary = "Creates an approved billing installment and its individual receivable")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Official operation result", content = @Content(schema = @Schema(implementation = BillingInstallmentApprovalResponse.class))),
            @ApiResponse(responseCode = "202", description = "Awaiting approval; no official financial change", content = @Content(schema = @Schema(implementation = BillingInstallmentApprovalResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")
    })
    public ResponseEntity<BillingInstallmentApprovalResponse> create(
            @Valid @RequestBody CreateApprovedBillingInstallmentRequest request
    ) {
        BillingInstallmentApprovalResult result = billingInstallmentManagementService.createApproved(
                new CreateApprovedBillingInstallmentCommand(
                        request.contractId(), request.reference(), request.description(), request.issuedOn(), request.dueOn(),
                        request.financialGroupId(), request.allocations().stream().map(this::toCommand).toList(),
                        request.justification()
                )
        );
        return ResponseEntity.status(result.billingInstallment() == null ? HttpStatus.ACCEPTED : HttpStatus.CREATED)
                .body(new BillingInstallmentApprovalResponse(
                        toInstallmentResponse(result.billingInstallment(), result.allocations()),
                        toReceivableResponse(result.receivable()),
                        result.changeRequest() == null ? null : result.changeRequest().getId(),
                        result.mode()
                ));
    }

    private BillingAllocationCommand toCommand(BillingAllocationRequest request) {
        return new BillingAllocationCommand(request.contractServiceId(), request.measurementContractCompositionId(),
                request.type(), request.allocatedAmount());
    }

    private BillingInstallmentResponse toInstallmentResponse(BillingInstallment installment, List<BillingAllocation> allocations) {
        if (installment == null) {
            return null;
        }
        List<BillingAllocationResponse> allocationResponses = allocations.stream()
                .map(allocation -> new BillingAllocationResponse(
                        allocation.getId(), allocation.getContractServiceId(), allocation.getMeasurementContractCompositionId(), allocation.getType(),
                        allocation.getAllocatedAmount(), allocation.getStatus()
                ))
                .toList();
        return new BillingInstallmentResponse(
                installment.getId(), installment.getContractId(), installment.getReference(), installment.getDescription(),
                installment.getIssuedOn(), installment.getDueOn(), installment.getStatus(), allocationResponses, currencies.forContract(installment.getContractId())
        );
    }

    private ReceivableResponse toReceivableResponse(Receivable receivable) {
        if (receivable == null) {
            return null;
        }
        return new ReceivableResponse(
                receivable.getId(), receivable.getContractId(), receivable.getBillingInstallmentId(),
                receivable.getFinancialGroupId(), receivable.getReference(), receivable.getIssuedOn(), receivable.getDueOn(),
                receivable.getOriginalAmount(), receivable.getStatus(), currencies.forContract(receivable.getContractId())
        );
    }
}
