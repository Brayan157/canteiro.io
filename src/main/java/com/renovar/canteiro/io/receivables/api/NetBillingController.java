package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.receivables.api.request.CreateNetBillingRequest;
import com.renovar.canteiro.io.receivables.api.response.BillingAllocationResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentApprovalResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingInstallmentResponse;
import com.renovar.canteiro.io.receivables.api.response.NetBillingCapacityResponse;
import com.renovar.canteiro.io.receivables.api.response.ReceivableResponse;
import com.renovar.canteiro.io.receivables.application.CreateNetBillingService;
import com.renovar.canteiro.io.receivables.application.ReadNetBillingCapacityUseCase;
import com.renovar.canteiro.io.receivables.application.FinancialCurrencyService;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Set;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Tag(name = "Net billing")
public class NetBillingController {
    private final CreateNetBillingService billing;
    private final ReadNetBillingCapacityUseCase capacity;
    private final FinancialCurrencyService currencies;
    @GetMapping(value = "/api/v1/company/billing-control/contracts/{contractId}/net-capacity/services", produces = "application/json")
    @Operation(summary = "Complete usable net capacity grouped by service", description = "Requires BILLING.READ and MEASUREMENTS.READ; tenant from session. Complete origin coverage before service pagination, including negotiated base and eligible compositions. Unusable capacity is explicitly separate, never included in availableNetAmount. Fixed serviceId lexicographic order, no free sort. Version is required for creation; GET reserves nothing.")
    @ApiResponses({@ApiResponse(responseCode = "200", useReturnTypeSchema = true), @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public com.renovar.canteiro.io.receivables.api.response.ServiceNetBillingCapacityResponse services(@PathVariable UUID contractId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size) {
        return com.renovar.canteiro.io.receivables.api.response.ServiceNetBillingCapacityResponse.from(capacity.services(contractId, new PageQuery(page, size, null).toPageable(Set.of())));
    }

    @PostMapping(value = "/api/v1/company/billing-installments/net/by-service", produces = "application/json")
    @Operation(summary = "Bills all available or partial net money per service with official origin resolution", description = "Effective BILLING.CREATE/request authority. Negotiated base first, then externalAcceptanceOn ascending, ties measurementId/versionId/compositionId lexicographically. No proportional consumption. Backend resolves exact allocations, consumes existing per-origin discount basis, preserves lineage. Same operation/body replays; changed body or stale capacity returns 409. 202 reserves nothing and freezes proposal origins; approval revalidates version and resolution, never expands ALL_AVAILABLE silently. Legacy net endpoint remains unchanged. At most 100 services and 10000 resolved origins.")
    @ApiResponses({@ApiResponse(responseCode = "201", useReturnTypeSchema = true), @ApiResponse(responseCode = "200", useReturnTypeSchema = true), @ApiResponse(responseCode = "202", useReturnTypeSchema = true), @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<BillingInstallmentApprovalResponse> byService(@Valid @RequestBody com.renovar.canteiro.io.receivables.api.request.CreateServiceNetBillingRequest request) {
        return result(billing.create(request.toCommand()));
    }
    @GetMapping(value = "/api/v1/company/billing-control/contracts/{contractId}/net-capacity", produces = "application/json")
    @Operation(summary = "Official proportional net billing capacity by exact financial origin",
            description = "Requires BILLING.READ and MEASUREMENTS.READ; tenant from session. Read-only, no reservation. Contract discount is distributed only over uncommitted gross capacity, with deterministic cent closure. Negotiated prices and accepted values do not change. Fixed ordering by base/composition and immutable IDs, ascending; no free sort. Filters do not change global distribution.")
    @ApiResponses({@ApiResponse(responseCode = "200", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public NetBillingCapacityResponse capacity(@PathVariable UUID contractId,
            @RequestParam(required = false) UUID contractServiceId, @RequestParam(required = false) UUID measurementContractCompositionId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size) {
        return NetBillingCapacityResponse.from(capacity.get(contractId, contractServiceId, measurementContractCompositionId,
                new PageQuery(page, size, null).toPageable(Set.of())));
    }

    @PostMapping(value = "/api/v1/company/billing-installments/net", produces = "application/json")
    @Operation(summary = "Creates a positive net installment, freezing its consumed gross basis and contract discount",
            description = "Requires effective BILLING.CREATE or its request/approval authority. No manual negative discount line. Send operationId for idempotency and capacityVersion from net-capacity. Same operation/body replays without new effects; different body or stale capacity returns 409. Pending 202 reserves nothing; approval revalidates capacity under contract lock. NF/receivable use only allocated net money; immutable basis preserves origin limits.")
    @ApiResponses({@ApiResponse(responseCode = "201", description = "Applied directly", content = @Content(schema = @Schema(implementation = BillingInstallmentApprovalResponse.class))),
            @ApiResponse(responseCode = "200", description = "Idempotent replay of the same operation", content = @Content(schema = @Schema(implementation = BillingInstallmentApprovalResponse.class))),
            @ApiResponse(responseCode = "202", description = "Pending approval; no official allocations or receivable", content = @Content(schema = @Schema(implementation = BillingInstallmentApprovalResponse.class))),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"), @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "409", ref = "#/components/responses/ProblemDetail")})
    public ResponseEntity<BillingInstallmentApprovalResponse> create(@Valid @RequestBody CreateNetBillingRequest request) {
        return result(billing.create(request.toCommand()));
    }
    private ResponseEntity<BillingInstallmentApprovalResponse> result(CreateNetBillingService.Result result) {
        var value = result.value();
        var installment = value.billingInstallment();var title = value.receivable();
        var response = new BillingInstallmentApprovalResponse(installment == null ? null : new BillingInstallmentResponse(installment.getId(),
                installment.getContractId(), installment.getReference(), installment.getDescription(), installment.getIssuedOn(), installment.getDueOn(),
                installment.getStatus(), value.allocations().stream().map(BillingAllocationResponse::from).toList(), currencies.forContract(installment.getContractId())),
                title == null ? null : new ReceivableResponse(title.getId(), title.getContractId(), title.getBillingInstallmentId(), title.getFinancialGroupId(),
                        title.getReference(), title.getIssuedOn(), title.getDueOn(), title.getOriginalAmount(), title.getStatus(), currencies.forContract(title.getContractId())),
                value.changeRequest() == null ? null : value.changeRequest().getId(), value.mode());
        return ResponseEntity.status(value.changeRequest() != null ? 202 : result.replayed() ? 200 : 201).body(response);
    }
}
