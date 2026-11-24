package com.renovar.canteiro.io.receivables.api;

import com.renovar.canteiro.io.contracts.domain.ContractBillingControlFilter;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.receivables.api.response.BillingAllocationControlResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingControlSummaryResponse;
import com.renovar.canteiro.io.receivables.api.response.ContractBillingControlResponse;
import com.renovar.canteiro.io.receivables.api.response.BillingServiceControlResponse;
import com.renovar.canteiro.io.receivables.application.BillingAllocationControl;
import com.renovar.canteiro.io.receivables.application.BillingControlSummary;
import com.renovar.canteiro.io.receivables.application.ContractBillingControl;
import com.renovar.canteiro.io.receivables.application.GetBillingControlUseCase;
import com.renovar.canteiro.io.receivables.application.FindBillableMeasurementOriginsUseCase;
import com.renovar.canteiro.io.receivables.api.response.BillableMeasurementOriginResponse;
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/billing-control/contracts")
@RequiredArgsConstructor
@Tag(name = "Billing control")
public class BillingControlController {
    private final GetBillingControlUseCase getBillingControlUseCase;
    private final FindBillableMeasurementOriginsUseCase findBillableMeasurementOriginsUseCase;

    @GetMapping(value = "/{contractId}/measurement-origins", produces = "application/json")
    @Operation(summary = "Lists available immutable measurement origins for contract billing",
            description = "Requires BILLING.READ and MEASUREMENTS.READ in the authenticated company. Only applied ACCEPTED compositions with positive origin capacity, no reversal and no unreconciled reduction are selectable. This GET reserves nothing; POST billing-installments revalidates origin, service and contract capacity under lock. Default order: createdAt desc, id desc.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page of eligible origins with canonical decimal strings", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<BillableMeasurementOriginResponse> origins(
            @PathVariable UUID contractId,
            @Parameter(description = "Optional exact measurement ID within the same contract and company; foreign/nonmatching IDs return an empty page.")
            @RequestParam(required = false) UUID measurementId,
            @Parameter(description = "Optional immutable destination service ID; foreign/nonmatching IDs return an empty page.")
            @RequestParam(required = false) UUID contractServiceId,
            @Parameter(schema = @Schema(minimum = "0", defaultValue = "0")) @RequestParam(required = false) Integer page,
            @Parameter(schema = @Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(required = false) Integer size,
            @Parameter(description = "Only createdAt,asc or createdAt,desc. Ties use composition ID in the same direction; default descending.")
            @RequestParam(required = false) List<String> sort
    ) {
        var result = findBillableMeasurementOriginsUseCase.find(contractId, measurementId, contractServiceId,
                new PageQuery(page, size, sort).toPageable(Set.of("createdAt")));
        return PageResponse.from(result.origins(), origin -> BillableMeasurementOriginResponse.from(origin, result.currency(), result.capacity()));
    }

    @GetMapping
    @Operation(summary = "Lists billing control balances owned exclusively by contracts",
            description = "Requires BILLING.READ. receivedAmount additionally requires effective RECEIVABLES.READ; otherwise receivedAmountAvailable=false and receivedAmount=null. Cash is the official signed sum of CONFIRMED receipts/reversals for each contract, including historical/renegotiated titles. No retention, credit, invoice or pending proposal is counted as cash. Tenant from session, totals computed by the backend in a consistent read; no write or local summation.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contract billing controls and permitted official cash aggregates", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail")
    })
    public PageResponse<BillingControlSummaryResponse> find(
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @Parameter(description = "Repeatable field,asc or field,desc. Fields: "
                    + "name, reference, status, createdAt, openFirst. "
                    + "openFirst,asc puts OPEN and ACTIVE first before pagination; desc reverses those groups. "
                    + "Within each group, explicit sorts apply first, followed by createdAt descending and id ascending.")
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(getBillingControlUseCase.find(new ContractBillingControlFilter(contractId, status, query),
                new PageQuery(page, size, sort).toPageable(Set.of("name", "reference", "status", "createdAt", "openFirst"))),
                this::summary);
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Shows contract services, installments and invoice balances for billing control",
            description = "Requires BILLING.READ. receivedAmount additionally requires effective RECEIVABLES.READ; otherwise receivedAmountAvailable=false and receivedAmount=null, never a fictitious zero. Official signed CONFIRMED receipts/reversals only, preserving tenant, currency and historical/renegotiated receipts. Retention/credits/NFs/202 are not cash; no allocation receipt eligibility is inferred from this contract aggregate.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Official contract control; cash is nullable according to permission", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "400", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "401", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "403", ref = "#/components/responses/ProblemDetail"),
            @ApiResponse(responseCode = "404", ref = "#/components/responses/ProblemDetail")
    })
    public ContractBillingControlResponse get(@PathVariable UUID contractId) {
        ContractBillingControl control = getBillingControlUseCase.get(contractId);
        return new ContractBillingControlResponse(control.contract().getId(), control.contract().getWorkId(),
                control.contract().getReference(), control.contract().getName(), control.contract().getStatus(),
                canonicalMoney(control.balance().serviceSubtotal()), canonicalMoney(control.balance().contractDiscountAmount()),
                canonicalMoney(control.balance().contractNetAmount()), canonicalMoney(control.balance().activeAllocatedAmount()),
                canonicalMoney(control.balance().availableAmount()),
                control.services().stream().map(service -> new BillingServiceControlResponse(service.contractServiceId(),
                        service.serviceName(), canonicalMoney(service.serviceNetAmount()), canonicalMoney(service.activeAllocatedAmount()),
                        canonicalMoney(service.availableAmount()), canonicalMoney(service.issuedInInvoicesAmount()),
                        canonicalMoney(service.activeCapacityAmount()), canonicalMoney(service.netAvailableToBillAmount()))).toList(),
                control.allocations().stream().map(this::allocation).toList(), control.contract().getCurrency(),
                control.receivedAmount() != null, control.receivedAmount() == null ? null : canonicalMoney(control.receivedAmount()));
    }

    private BillingControlSummaryResponse summary(BillingControlSummary value) {
        return new BillingControlSummaryResponse(value.contract().getId(), value.contract().getWorkId(),
                value.contract().getReference(), value.contract().getName(), value.contract().getStatus(),
                value.balance().contractNetAmount(), value.balance().activeAllocatedAmount(), value.balance().availableAmount(), value.contract().getCurrency(),
                value.receivedAmount() != null, value.receivedAmount());
    }

    private BillingAllocationControlResponse allocation(BillingAllocationControl value) {
        return new BillingAllocationControlResponse(value.allocation().getId(), value.installment().getId(),
                value.installment().getReference(), value.installment().getDueOn(), value.installment().getStatus(),
                value.allocation().getContractServiceId(), value.serviceName(), value.allocation().getType(),
                value.allocation().getStatus(), canonicalMoney(value.allocation().getAllocatedAmount()),
                canonicalMoney(value.invoiceBalance().registeredInvoiceAmount()),
                canonicalMoney(value.invoiceBalance().availableInvoiceAmount()),
                value.receivableFinancialStatus() != null, value.receivableFinancialStatus(), value.receivableReceivedWithWithholding());
    }

    private String canonicalMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
