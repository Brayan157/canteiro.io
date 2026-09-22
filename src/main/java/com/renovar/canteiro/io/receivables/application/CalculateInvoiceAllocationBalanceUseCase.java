package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAllocationBalance;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CalculateInvoiceAllocationBalanceUseCase {

    private final BillingAllocationRepository billingAllocationRepository;
    private final InvoiceItemRepository invoiceItemRepository;

    public InvoiceAllocationBalance calculate(UUID companyId, UUID billingAllocationId) {
        BillingAllocation allocation = billingAllocationRepository.findByIdAndCompanyId(billingAllocationId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Billing allocation"));
        return InvoiceAllocationBalance.from(allocation,
                invoiceItemRepository.registeredAmountByBillingAllocationIdAndCompanyId(billingAllocationId, companyId));
    }
}
