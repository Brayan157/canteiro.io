package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface MeasurementDocumentJpaRepository extends JpaRepository<MeasurementDocumentJpaEntity, UUID> {
    Optional<MeasurementDocumentJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);
}
