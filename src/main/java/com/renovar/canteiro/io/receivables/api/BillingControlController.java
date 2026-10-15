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
                canonicalMoney(control.balance().serviceSubtotal()), canonicalMoney(control.balance().contractDiscountAmount()),
                canonicalMoney(control.balance().contractNetAmount()), canonicalMoney(control.balance().activeAllocatedAmount()),
                canonicalMoney(control.balance().availableAmount()),
                control.services().stream().map(service -> new BillingServiceControlResponse(service.contractServiceId(),
                        service.serviceName(), canonicalMoney(service.serviceNetAmount()), canonicalMoney(service.activeAllocatedAmount()),
                        canonicalMoney(service.availableAmount()), canonicalMoney(service.issuedInInvoicesAmount()))).toList(),
                control.allocations().stream().map(this::allocation).toList(), control.contract().getCurrency());
    }

    private BillingControlSummaryResponse summary(BillingControlSummary value) {
        return new BillingControlSummaryResponse(value.contract().getId(), value.contract().getWorkId(),
                value.contract().getReference(), value.contract().getName(), value.contract().getStatus(),
                value.balance().contractNetAmount(), value.balance().activeAllocatedAmount(), value.balance().availableAmount(), value.contract().getCurrency());
    }

    private BillingAllocationControlResponse allocation(BillingAllocationControl value) {
        return new BillingAllocationControlResponse(value.allocation().getId(), value.installment().getId(),
                value.installment().getReference(), value.installment().getDueOn(), value.installment().getStatus(),
                value.allocation().getContractServiceId(), value.serviceName(), value.allocation().getType(),
                value.allocation().getStatus(), canonicalMoney(value.allocation().getAllocatedAmount()),
                canonicalMoney(value.invoiceBalance().registeredInvoiceAmount()),
                canonicalMoney(value.invoiceBalance().availableInvoiceAmount()));
    }

    private String canonicalMoney(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
