package com.renovar.canteiro.io.contracts.domain;

import java.util.List;
import java.util.UUID;

public interface ContractServiceRepository {

    ContractService save(ContractService contractService);

    List<ContractService> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
