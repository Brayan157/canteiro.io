package com.renovar.canteiro.io.contracts.domain;

import java.util.UUID;

public interface ContractRevisionRepository {

    ContractRevision save(ContractRevision revision);

    int nextRevisionNumber(UUID contractId, UUID companyId);
}
