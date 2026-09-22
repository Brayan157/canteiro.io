package com.renovar.canteiro.io.receivables.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BillingAllocationRepository {

    BillingAllocation save(BillingAllocation billingAllocation);

    Optional<BillingAllocation> findByIdAndCompanyId(UUID id, UUID companyId);

    List<BillingAllocation> findByBillingInstallmentIdAndCompanyId(UUID billingInstallmentId, UUID companyId);

    List<BillingAllocation> findByContractIdAndCompanyId(UUID contractId, UUID companyId);

    BillingAllocation updateStatus(BillingAllocation billingAllocation);
}
