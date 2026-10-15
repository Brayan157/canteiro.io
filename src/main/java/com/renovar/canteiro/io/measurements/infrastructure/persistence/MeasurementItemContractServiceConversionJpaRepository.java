package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

interface MeasurementItemContractServiceConversionJpaRepository
        extends JpaRepository<MeasurementItemContractServiceConversionJpaEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("select count(c)>0 from MeasurementItemContractServiceConversionJpaEntity c "
        +"where c.companyId=:company and c.measurementVersionId in (select v.id from MeasurementVersionJpaEntity v "
        +"where v.companyId=:company and v.measurementId=:mid)")
    boolean existsForMeasurement(@org.springframework.data.repository.query.Param("mid") UUID mid,
        @org.springframework.data.repository.query.Param("company") UUID company);

    Optional<MeasurementItemContractServiceConversionJpaEntity> findByMeasurementItemIdAndCompanyId(UUID measurementItemId,
                                                                                                        UUID companyId);

    List<MeasurementItemContractServiceConversionJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                                      UUID companyId);
}
