package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface MeasurementContractCompositionJpaRepository extends JpaRepository<MeasurementContractCompositionJpaEntity, UUID> {
    Optional<MeasurementContractCompositionJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<MeasurementContractCompositionJpaEntity> findByMeasurementServiceIdAndCompanyId(UUID measurementServiceId, UUID companyId);
    List<MeasurementContractCompositionJpaEntity> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
    List<MeasurementContractCompositionJpaEntity> findByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId);
}
