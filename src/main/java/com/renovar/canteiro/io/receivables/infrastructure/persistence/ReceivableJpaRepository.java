package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface ReceivableJpaRepository extends JpaRepository<ReceivableJpaEntity, UUID> {

    Optional<ReceivableJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("""
            select receivable from ReceivableJpaEntity receivable
            where receivable.id = :id and receivable.companyId = :companyId
            """)
    Optional<ReceivableJpaEntity> findByIdAndCompanyIdWithSettlementLock(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );

    Optional<ReceivableJpaEntity> findByBillingInstallmentIdAndCompanyIdAndRenegotiationSourceReceivableIdIsNull(UUID billingInstallmentId, UUID companyId);
    java.util.List<ReceivableJpaEntity> findAllByBillingInstallmentIdAndCompanyId(UUID installmentId, UUID companyId);
    java.util.List<ReceivableJpaEntity> findByRenegotiationSourceReceivableIdAndCompanyId(UUID sourceId,UUID companyId);
}
