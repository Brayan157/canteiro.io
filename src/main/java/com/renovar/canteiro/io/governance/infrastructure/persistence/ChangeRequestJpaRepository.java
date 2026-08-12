package com.renovar.canteiro.io.governance.infrastructure.persistence;

import jakarta.persistence.LockModeType;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

interface ChangeRequestJpaRepository extends JpaRepository<ChangeRequestJpaEntity, UUID> {

    Optional<ChangeRequestJpaEntity> findByIdAndCompanyId(UUID changeRequestId, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ChangeRequestJpaEntity> findWithLockByIdAndCompanyId(UUID changeRequestId, UUID companyId);

    Page<ChangeRequestJpaEntity> findByCompanyId(UUID companyId, Pageable pageable);

    Page<ChangeRequestJpaEntity> findByCompanyIdAndStatus(
            UUID companyId,
            ChangeRequestStatus status,
            Pageable pageable
    );
}
