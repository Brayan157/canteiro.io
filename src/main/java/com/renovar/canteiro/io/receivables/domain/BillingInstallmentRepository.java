package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface BillingInstallmentRepository {

    org.springframework.data.domain.Page<BillingInstallmentSummary> findByCompanyId(UUID companyId,
            BillingInstallmentFilter filter, org.springframework.data.domain.Pageable pageable);

    BillingInstallment save(BillingInstallment billingInstallment);

    Optional<BillingInstallment> findByIdAndCompanyId(UUID id, UUID companyId);

    BillingInstallment updateStatus(BillingInstallment billingInstallment);
}
