package com.renovar.canteiro.io.payables.domain;

import java.util.Optional;
import java.util.UUID;

public interface PayableRepository {

    Payable save(Payable payable);

    Optional<Payable> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId);

    Optional<Payable> findByTaxObligationIdAndCompanyIdWithLock(UUID taxObligationId, UUID companyId);

    Payable updateStatus(Payable payable);

    Optional<Payable> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<Payable> findByIdAndCompanyIdWithLock(UUID id, UUID companyId);
    org.springframework.data.domain.Page<Payable> find(UUID companyId, PayableFilter filter, org.springframework.data.domain.Pageable page);
    java.util.List<Payable> findByGroup(UUID companyId, UUID groupId);
    void lockCreation(UUID companyId, UUID operationId);
    void lockCatalogReferences(UUID companyId, UUID supplierId, UUID categoryId);
    Optional<CreationGroup> findCreation(UUID companyId, UUID operationId);
    UUID createGroup(UUID companyId, UUID operationId, String payloadHash);
    record CreationGroup(UUID id, String payloadHash) { }
}
