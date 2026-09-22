package com.renovar.canteiro.io.receivables.domain;

import java.util.Optional;
import java.util.UUID;

public interface BillingInstallmentRepository {

    BillingInstallment save(BillingInstallment billingInstallment);

    Optional<BillingInstallment> findByIdAndCompanyId(UUID id, UUID companyId);

    BillingInstallment updateStatus(BillingInstallment billingInstallment);
}
