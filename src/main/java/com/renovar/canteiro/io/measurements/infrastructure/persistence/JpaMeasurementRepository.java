package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.Measurement;
import com.renovar.canteiro.io.measurements.domain.MeasurementRepository;
import com.renovar.canteiro.io.measurements.application.MeasurementFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        int updated = measurementJpaRepository.updateStateIfCurrent(measurement.getId(), measurement.getCompanyId(),
                measurement.getLockVersion(), measurement.getStatus(), measurement.getWorkId(), measurement.getContractId());
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

    @Override
    public Page<Measurement> findByCompanyId(UUID companyId, MeasurementFilter filter, Pageable pageable) {
        return measurementJpaRepository.findAll(specification(companyId, filter), pageable).map(this::toDomain);
    }

    private Specification<MeasurementJpaEntity> specification(UUID companyId, MeasurementFilter filter) {
        return (root, query, builder) -> {
            var predicate = builder.equal(root.get("companyId"), companyId);
            if (filter.workId() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("workId"), filter.workId()));
            }
            if (filter.contractId() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("contractId"), filter.contractId()));
            }
            if (filter.normalizedReference() != null) {
                predicate = builder.and(predicate, builder.like(builder.lower(root.<String>get("reference")),
                        "%" + filter.normalizedReference().toLowerCase(java.util.Locale.ROOT) + "%"));
            }
            if (filter.status() != null) {
                predicate = builder.and(predicate, builder.equal(root.get("status"), filter.status()));
            }
            if (filter.measuredOnFrom() != null) {
                predicate = builder.and(predicate, builder.greaterThanOrEqualTo(
                        root.<java.time.LocalDate>get("measuredOn"), filter.measuredOnFrom()));
            }
            if (filter.measuredOnTo() != null) {
                predicate = builder.and(predicate, builder.lessThanOrEqualTo(
                        root.<java.time.LocalDate>get("measuredOn"), filter.measuredOnTo()));
            }
            return predicate;
        };
    }

    private Measurement toDomain(MeasurementJpaEntity entity) {
        return Measurement.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getWorkId(), entity.getContractId(), entity.getTitle(), entity.getReference(),
                entity.getDescription(), entity.getMeasuredOn(), entity.getStatus(), entity.getLockVersion(),
                entity.getCreatedAt(), entity.getUpdatedAt(), entity.getCurrency()
        );
    }

    private MeasurementJpaEntity measurementItem(Measurement measurement) {
        return new MeasurementJpaEntity(measurement.getCompanyId(), measurement.getWorkId(), measurement.getContractId(),
                measurement.getTitle(), measurement.getReference(), measurement.getDescription(), measurement.getMeasuredOn(), measurement.getStatus(), measurement.getCurrency());
    }
}
