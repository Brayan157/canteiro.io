package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;
import com.renovar.canteiro.io.measurements.domain.MeasurementVersionStatus;

import java.time.LocalDate;

import java.util.Optional;
import java.util.UUID;

interface MeasurementVersionJpaRepository extends JpaRepository<MeasurementVersionJpaEntity, UUID> {

    Optional<MeasurementVersionJpaEntity> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MeasurementVersionJpaEntity> findWithLockByIdAndCompanyId(UUID id, UUID companyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query("update MeasurementVersionJpaEntity version set version.status = :status, "
            + "version.externalAcceptanceOn = :externalAcceptanceOn, "
            + "version.externalAcceptanceNotes = :externalAcceptanceNotes, "
            + "version.lockVersion = version.lockVersion + 1 "
            + "where version.id = :id and version.companyId = :companyId and version.lockVersion = :lockVersion")
    int updateLifecycleIfCurrent(@Param("id") UUID id, @Param("companyId") UUID companyId,
                                 @Param("lockVersion") int lockVersion, @Param("status") MeasurementVersionStatus status,
                                 @Param("externalAcceptanceOn") LocalDate externalAcceptanceOn,
                                 @Param("externalAcceptanceNotes") String externalAcceptanceNotes);

    Optional<MeasurementVersionJpaEntity> findTopByMeasurementIdAndCompanyIdOrderByVersionNumberDesc(
            UUID measurementId, UUID companyId
    );
}
