package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

interface MeasurementContractAdjustmentJpaRepository
        extends JpaRepository<MeasurementContractAdjustmentJpaEntity, UUID> {

    Optional<MeasurementContractAdjustmentJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                              UUID companyId);

    @Query("select coalesce(sum(adjustment.adjustmentAmount), 0) from MeasurementContractAdjustmentJpaEntity adjustment "
            + "where adjustment.contractId = :contractId and adjustment.companyId = :companyId")
    BigDecimal sumByContractIdAndCompanyId(@Param("contractId") UUID contractId, @Param("companyId") UUID companyId);
}
