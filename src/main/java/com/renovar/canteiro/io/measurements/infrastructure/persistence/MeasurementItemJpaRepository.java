package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MeasurementItemJpaRepository extends JpaRepository<MeasurementItemJpaEntity, UUID> {

    Optional<MeasurementItemJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeasurementItemJpaEntity> findWithLockByIdAndCompanyId(UUID id, UUID companyId);

    List<MeasurementItemJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);
}
