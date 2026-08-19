package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface MeasurementDiscountJpaRepository extends JpaRepository<MeasurementDiscountJpaEntity, UUID> {

    Optional<MeasurementDiscountJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);
}
