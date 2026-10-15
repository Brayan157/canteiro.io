package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.renovar.canteiro.io.measurements.domain.MeasurementStatus;

import java.util.Optional;
import java.util.UUID;

interface MeasurementJpaRepository extends JpaRepository<MeasurementJpaEntity, UUID>, JpaSpecificationExecutor<MeasurementJpaEntity> {

    Optional<MeasurementJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update MeasurementJpaEntity measurement set measurement.status = :status, "
            + "measurement.workId = :workId, measurement.contractId = :contractId, "
            + "measurement.lockVersion = measurement.lockVersion + 1 "
            + "where measurement.id = :id and measurement.companyId = :companyId "
            + "and measurement.lockVersion = :lockVersion")
    int updateStateIfCurrent(@Param("id") UUID id, @Param("companyId") UUID companyId,
                             @Param("lockVersion") int lockVersion, @Param("status") MeasurementStatus status,
                             @Param("workId") UUID workId, @Param("contractId") UUID contractId);
}
