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

    // Validation-only locking must not advance official versions in a 202 proposal.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select contract from ContractJpaEntity contract where contract.id = :id and contract.companyId = :companyId")
    Optional<ContractJpaEntity> findByIdAndCompanyIdWithCancellationCheckLock(@Param("id") UUID id, @Param("companyId") UUID companyId);

    Page<ContractJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);

    @Query(value = "select id from obra where id = :workId and company_id = :companyId for no key update", nativeQuery = true)
    Optional<UUID> lockWorkForReference(@Param("companyId") UUID companyId, @Param("workId") UUID workId);

    @Query(value = """
            select exists (
                select 1 from contract
                where company_id = :companyId and work_id = :workId
                  and id is distinct from cast(:contractId as uuid)
                  and lower(cast(:previousReference as text)) is distinct from lower(:reference)
                  and lower(regexp_replace(reference, '^[[:space:]]+|[[:space:]]+$', '', 'g')) = lower(:reference)
            )
            """, nativeQuery = true)
    boolean existsConflictingReference(@Param("companyId") UUID companyId, @Param("workId") UUID workId,
                                      @Param("contractId") UUID contractId, @Param("previousReference") String previousReference,
                                      @Param("reference") String reference);


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
