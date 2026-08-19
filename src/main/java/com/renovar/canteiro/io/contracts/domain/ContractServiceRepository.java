package com.renovar.canteiro.io.contracts.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContractServiceRepository {

    ContractService save(ContractService contractService);

    Optional<ContractService> findByIdAndCompanyId(UUID id, UUID companyId);

    List<ContractService> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
