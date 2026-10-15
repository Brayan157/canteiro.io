package com.renovar.canteiro.io.contracts.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

interface ContractJpaRepository extends JpaRepository<ContractJpaEntity, UUID>, JpaSpecificationExecutor<ContractJpaEntity> {

    Optional<ContractJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<ContractJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);

    // Capacity checks must observe the previous writer's committed allocations before
    // admitting another origin. Keep the version increment and hold a database write lock.
    @Lock(LockModeType.PESSIMISTIC_FORCE_INCREMENT)
    @Query("""
            select contract from ContractJpaEntity contract
            where contract.id = :id and contract.companyId = :companyId
            """)
    Optional<ContractJpaEntity> findByIdAndCompanyIdWithBillingLock(
            @Param("id") UUID id,
            @Param("companyId") UUID companyId
    );
}
