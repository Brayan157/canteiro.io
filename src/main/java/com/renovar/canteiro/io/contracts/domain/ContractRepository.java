package com.renovar.canteiro.io.contracts.domain;

import java.util.Optional;
import java.util.UUID;

public interface ContractRepository {

    Contract save(Contract contract);

    Optional<Contract> findByIdAndCompanyId(UUID id, UUID companyId);
}
