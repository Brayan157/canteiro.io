package com.renovar.canteiro.io.payables.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PayableJpaRepository extends JpaRepository<PayableJpaEntity, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<PayableJpaEntity> {

    Optional<PayableJpaEntity> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PayableJpaEntity p where p.taxObligationId = :taxObligationId and p.companyId = :companyId")
    Optional<PayableJpaEntity> findByTaxObligationIdAndCompanyIdWithLock(UUID taxObligationId, UUID companyId);
    Optional<PayableJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from PayableJpaEntity p where p.id = :id and p.companyId = :companyId")
    Optional<PayableJpaEntity> findByIdAndCompanyIdWithLock(UUID id, UUID companyId);
    java.util.List<PayableJpaEntity> findByCompanyIdAndFinancialGroupIdOrderByCreatedAtAscIdAsc(UUID companyId,UUID groupId);
}
