package com.renovar.canteiro.io.contracts.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContractRepository {

    Contract save(Contract contract);

    Optional<Contract> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Contract> findByIdAndCompanyIdWithBillingLock(UUID id, UUID companyId);

    Page<Contract> findByCompanyId(UUID companyId, Pageable pageable);

    Page<Contract> findBillingControl(UUID companyId, ContractBillingControlFilter filter, Pageable pageable);
}
