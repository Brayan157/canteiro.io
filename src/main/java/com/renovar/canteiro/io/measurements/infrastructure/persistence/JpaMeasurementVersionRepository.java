package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementVersion;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementVersionRepository implements MeasurementVersionRepository {

    private final MeasurementVersionJpaRepository measurementVersionJpaRepository;

    @Override
    @org.springframework.transaction.annotation.Transactional
    public MeasurementVersion save(MeasurementVersion measurementVersion) {
        if (measurementVersion.getId() == null) {
            MeasurementVersionJpaEntity entity = new MeasurementVersionJpaEntity(
                    measurementVersion.getCompanyId(), measurementVersion.getMeasurementId(),
                    measurementVersion.getVersionNumber(), measurementVersion.getPreviousVersionId(),
                    measurementVersion.getStatus()
            );
            return toDomain(measurementVersionJpaRepository.save(entity));
        }
        MeasurementVersionJpaEntity existing = measurementVersionJpaRepository
                .findWithLockByIdAndCompanyId(measurementVersion.getId(), measurementVersion.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Measurement version does not exist"));
        if (existing.getLockVersion() != measurementVersion.getLockVersion()) {
            throw new ObjectOptimisticLockingFailureException(MeasurementVersionJpaEntity.class, measurementVersion.getId());
        }
        if(existing.getStatus()==measurementVersion.getStatus()
            &&java.util.Objects.equals(existing.getExternalAcceptanceOn(),measurementVersion.getExternalAcceptanceOn())
            &&java.util.Objects.equals(existing.getExternalAcceptanceNotes(),measurementVersion.getExternalAcceptanceNotes())
            &&existing.getCorrectionItemIds().equals(new LinkedHashSet<>(measurementVersion.getCorrectionItemIds()))) {
            int updated=measurementVersionJpaRepository.updateLifecycleIfCurrent(existing.getId(),existing.getCompanyId(),
                existing.getLockVersion(),existing.getStatus(),existing.getExternalAcceptanceOn(),existing.getExternalAcceptanceNotes());
            if(updated!=1)throw new ObjectOptimisticLockingFailureException(MeasurementVersionJpaEntity.class,measurementVersion.getId());
            return toDomain(measurementVersionJpaRepository.findByIdAndCompanyId(measurementVersion.getId(),measurementVersion.getCompanyId()).orElseThrow());
        }
        existing.updateLifecycle(measurementVersion.getStatus(), measurementVersion.getExternalAcceptanceOn(),
                measurementVersion.getExternalAcceptanceNotes(), new LinkedHashSet<>(measurementVersion.getCorrectionItemIds()));
        return toDomain(measurementVersionJpaRepository.saveAndFlush(existing));
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

    @Override
    public List<MeasurementVersion> findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(UUID measurementId,
                                                                                              UUID companyId) {
        return measurementVersionJpaRepository.findByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(measurementId,
                companyId).stream().map(this::toDomain).toList();
    }

    private MeasurementVersion toDomain(MeasurementVersionJpaEntity entity) {
        return MeasurementVersion.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getMeasurementId(), entity.getVersionNumber(),
                entity.getPreviousVersionId(), entity.getStatus(), entity.getLockVersion(), entity.getExternalAcceptanceOn(),
                entity.getExternalAcceptanceNotes(), entity.getCorrectionItemIds().stream().toList(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
