package com.renovar.canteiro.io.governance.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ChangeRequestRepository {

    ChangeRequest save(ChangeRequest changeRequest);

    Optional<ChangeRequest> findByIdAndCompanyId(UUID changeRequestId, UUID companyId);

    Optional<ChangeRequest> findWithLockByIdAndCompanyId(UUID changeRequestId, UUID companyId);

    Page<ChangeRequest> findByCompanyId(UUID companyId, Pageable pageable);

    Page<ChangeRequest> findByCompanyIdAndStatus(
            UUID companyId,
            ChangeRequestStatus status,
            Pageable pageable
    );
}
