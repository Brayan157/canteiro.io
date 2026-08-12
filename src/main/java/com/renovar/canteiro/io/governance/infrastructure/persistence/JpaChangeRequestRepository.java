package com.renovar.canteiro.io.governance.infrastructure.persistence;

import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaChangeRequestRepository implements ChangeRequestRepository {

    private final ChangeRequestJpaRepository changeRequestJpaRepository;
    private final ChangeRequestPersistenceMapper changeRequestPersistenceMapper;

    @Override
    public ChangeRequest save(ChangeRequest changeRequest) {
        if (changeRequest.getId() == null) {
            return changeRequestPersistenceMapper.toDomain(
                    changeRequestJpaRepository.save(changeRequestPersistenceMapper.toJpaEntity(changeRequest))
            );
        }
        ChangeRequestJpaEntity entity = changeRequestJpaRepository.findById(changeRequest.getId())
                .orElseThrow(() -> new IllegalStateException("Change request must exist before it can be updated"));
        changeRequestPersistenceMapper.updateJpaEntity(entity, changeRequest);
        return changeRequestPersistenceMapper.toDomain(
                changeRequestJpaRepository.save(entity)
        );
    }

    @Override
    public Optional<ChangeRequest> findByIdAndCompanyId(UUID changeRequestId, UUID companyId) {
        return changeRequestJpaRepository.findByIdAndCompanyId(changeRequestId, companyId)
                .map(changeRequestPersistenceMapper::toDomain);
    }

    @Override
    public Optional<ChangeRequest> findWithLockByIdAndCompanyId(UUID changeRequestId, UUID companyId) {
        return changeRequestJpaRepository.findWithLockByIdAndCompanyId(changeRequestId, companyId)
                .map(changeRequestPersistenceMapper::toDomain);
    }

    @Override
    public Page<ChangeRequest> findByCompanyId(UUID companyId, Pageable pageable) {
        return changeRequestJpaRepository.findByCompanyId(companyId, pageable).map(changeRequestPersistenceMapper::toDomain);
    }

    @Override
    public Page<ChangeRequest> findByCompanyIdAndStatus(
            UUID companyId,
            ChangeRequestStatus status,
            Pageable pageable
    ) {
        return changeRequestJpaRepository.findByCompanyIdAndStatus(companyId, status, pageable)
                .map(changeRequestPersistenceMapper::toDomain);
    }
}
