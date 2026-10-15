package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MeasurementServiceJpaRepository extends JpaRepository<MeasurementServiceJpaEntity, UUID> {
    Optional<MeasurementServiceJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    List<MeasurementServiceJpaEntity> findByMeasurementVersionIdAndCompanyIdOrderBySortOrderAsc(UUID measurementVersionId, UUID companyId);
}
