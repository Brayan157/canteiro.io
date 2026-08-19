package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

interface MeasurementItemContractServiceConversionJpaRepository
        extends JpaRepository<MeasurementItemContractServiceConversionJpaEntity, UUID> {

    Optional<MeasurementItemContractServiceConversionJpaEntity> findByMeasurementItemIdAndCompanyId(UUID measurementItemId,
                                                                                                        UUID companyId);

    List<MeasurementItemContractServiceConversionJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                                      UUID companyId);
}
