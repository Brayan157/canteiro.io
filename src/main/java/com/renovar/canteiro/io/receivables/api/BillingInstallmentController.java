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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/billing-installments")
@RequiredArgsConstructor
@Tag(name = "Billing installments")
public class BillingInstallmentController {

    private final BillingInstallmentManagementService billingInstallmentManagementService;
    private final FinancialCancellationService financialCancellationService;

    @PostMapping("/{installmentId}/cancellation")
    @Operation(summary = "Cancels an installment, its receivable and allocations atomically")
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
        return new BillingAllocationCommand(request.contractServiceId(), request.type(), request.allocatedAmount());
    }

    private BillingInstallmentResponse toInstallmentResponse(BillingInstallment installment, List<BillingAllocation> allocations) {
        if (installment == null) {
            return null;
        }
        List<BillingAllocationResponse> allocationResponses = allocations.stream()
                .map(allocation -> new BillingAllocationResponse(
                        allocation.getId(), allocation.getContractServiceId(), allocation.getType(),
                        allocation.getAllocatedAmount(), allocation.getStatus()
                ))
                .toList();
        return new BillingInstallmentResponse(
                installment.getId(), installment.getContractId(), installment.getReference(), installment.getDescription(),
                installment.getIssuedOn(), installment.getDueOn(), installment.getStatus(), allocationResponses
        );
    }

    private ReceivableResponse toReceivableResponse(Receivable receivable) {
        if (receivable == null) {
            return null;
        }
        return new ReceivableResponse(
                receivable.getId(), receivable.getContractId(), receivable.getBillingInstallmentId(),
                receivable.getFinancialGroupId(), receivable.getReference(), receivable.getIssuedOn(), receivable.getDueOn(),
                receivable.getOriginalAmount(), receivable.getStatus()
        );
    }
}
