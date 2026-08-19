package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementVersionRepository implements MeasurementVersionRepository {

    private final MeasurementVersionJpaRepository measurementVersionJpaRepository;

    @Override
    public MeasurementVersion save(MeasurementVersion measurementVersion) {
        if (measurementVersion.getId() == null) {
            MeasurementVersionJpaEntity entity = new MeasurementVersionJpaEntity(
                    measurementVersion.getCompanyId(), measurementVersion.getMeasurementId(),
                    measurementVersion.getVersionNumber(), measurementVersion.getPreviousVersionId(),
                    measurementVersion.getStatus()
            );
            return toDomain(measurementVersionJpaRepository.save(entity));
        }
        int updated = measurementVersionJpaRepository.updateLifecycleIfCurrent(measurementVersion.getId(),
                measurementVersion.getCompanyId(), measurementVersion.getLockVersion(), measurementVersion.getStatus(),
                measurementVersion.getExternalAcceptanceOn(), measurementVersion.getExternalAcceptanceNotes());
        if (updated != 1) {
            throw new ObjectOptimisticLockingFailureException(MeasurementVersionJpaEntity.class, measurementVersion.getId());
        }
        return measurementVersionJpaRepository.findByIdAndCompanyId(measurementVersion.getId(), measurementVersion.getCompanyId())
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Measurement version does not exist"));
    }

    @Override
    public Optional<MeasurementVersion> findByIdAndCompanyId(UUID id, UUID companyId) {
        return measurementVersionJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<MeasurementVersion> findWithLockByIdAndCompanyId(UUID id, UUID companyId) {
        return measurementVersionJpaRepository.findWithLockByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<MeasurementVersion> findLatestByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId) {
        return measurementVersionJpaRepository.findTopByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(
                measurementId, companyId
        ).map(this::toDomain);
    }

    private MeasurementVersion toDomain(MeasurementVersionJpaEntity entity) {
        return MeasurementVersion.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getMeasurementId(), entity.getVersionNumber(),
                entity.getPreviousVersionId(), entity.getStatus(), entity.getLockVersion(), entity.getExternalAcceptanceOn(),
                entity.getExternalAcceptanceNotes(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
