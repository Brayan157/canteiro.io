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
import com.renovar.canteiro.io.shared.api.pagination.PageQuery;
import com.renovar.canteiro.io.shared.api.pagination.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/company/billing-control/contracts")
@RequiredArgsConstructor
@Tag(name = "Billing control")
public class BillingControlController {
    private final GetBillingControlUseCase getBillingControlUseCase;

    @GetMapping
    @Operation(summary = "Lists billing control balances owned exclusively by contracts")
    public PageResponse<BillingControlSummaryResponse> find(
            @RequestParam(required = false) UUID contractId,
            @RequestParam(required = false) ContractStatus status,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) List<String> sort
    ) {
        return PageResponse.from(getBillingControlUseCase.find(new ContractBillingControlFilter(contractId, status, query),
                new PageQuery(page, size, sort).toPageable(Set.of("name", "reference", "status", "createdAt"))),
                this::summary);
    }

    @GetMapping("/{contractId}")
    @Operation(summary = "Shows contract services, installments and invoice balances for billing control")
    public ContractBillingControlResponse get(@PathVariable UUID contractId) {
        ContractBillingControl control = getBillingControlUseCase.get(contractId);
        return new ContractBillingControlResponse(control.contract().getId(), control.contract().getWorkId(),
                control.contract().getReference(), control.contract().getName(), control.contract().getStatus(),
                control.balance().contractNetAmount(), control.balance().activeAllocatedAmount(), control.balance().availableAmount(),
                control.services().stream().map(service -> new BillingServiceControlResponse(service.contractServiceId(),
                        service.serviceName(), service.serviceNetAmount(), service.activeAllocatedAmount(),
                        service.availableAmount(), service.issuedInInvoicesAmount())).toList(),
                control.allocations().stream().map(this::allocation).toList());
    }

    private BillingControlSummaryResponse summary(BillingControlSummary value) {
        return new BillingControlSummaryResponse(value.contract().getId(), value.contract().getWorkId(),
                value.contract().getReference(), value.contract().getName(), value.contract().getStatus(),
                value.balance().contractNetAmount(), value.balance().activeAllocatedAmount(), value.balance().availableAmount());
    }

    private BillingAllocationControlResponse allocation(BillingAllocationControl value) {
        return new BillingAllocationControlResponse(value.allocation().getId(), value.installment().getId(),
                value.installment().getReference(), value.installment().getDueOn(), value.installment().getStatus(),
                value.allocation().getContractServiceId(), value.serviceName(), value.allocation().getType(),
                value.allocation().getStatus(), value.allocation().getAllocatedAmount(),
                value.invoiceBalance().registeredInvoiceAmount(), value.invoiceBalance().availableInvoiceAmount());
    }
}
