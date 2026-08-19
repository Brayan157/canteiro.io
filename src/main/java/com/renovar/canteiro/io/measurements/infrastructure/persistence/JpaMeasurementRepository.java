package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementRepository implements MeasurementRepository {

    private final MeasurementJpaRepository measurementJpaRepository;

    @Override
    public Measurement save(Measurement measurement) {
        if (measurement.getId() == null) {
            MeasurementJpaEntity entity = measurementItem(measurement);
            return toDomain(measurementJpaRepository.save(entity));
        }
        int updated = measurementJpaRepository.updateStatusIfCurrent(measurement.getId(), measurement.getCompanyId(),
                measurement.getLockVersion(), measurement.getStatus());
        if (updated != 1) {
            throw new ObjectOptimisticLockingFailureException(MeasurementJpaEntity.class, measurement.getId());
        }
        return measurementJpaRepository.findByIdAndCompanyId(measurement.getId(), measurement.getCompanyId())
                .map(this::toDomain)
                .orElseThrow(() -> new IllegalArgumentException("Measurement does not exist"));
    }

    @Override
    public Optional<Measurement> findByIdAndCompanyId(UUID id, UUID companyId) {
        return measurementJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    private Measurement toDomain(MeasurementJpaEntity entity) {
        return Measurement.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getWorkId(), entity.getContractId(), entity.getReference(),
                entity.getDescription(), entity.getMeasuredOn(), entity.getStatus(), entity.getLockVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private MeasurementJpaEntity measurementItem(Measurement measurement) {
        return new MeasurementJpaEntity(measurement.getCompanyId(), measurement.getWorkId(), measurement.getContractId(),
                measurement.getReference(), measurement.getDescription(), measurement.getMeasuredOn(), measurement.getStatus());
    }
}
