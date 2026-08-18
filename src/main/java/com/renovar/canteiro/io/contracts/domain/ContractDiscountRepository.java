package com.renovar.canteiro.io.contracts.domain;

import java.util.Optional;
import java.util.UUID;

public interface ContractDiscountRepository {

    ContractDiscount save(ContractDiscount contractDiscount);

    Optional<ContractDiscount> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
