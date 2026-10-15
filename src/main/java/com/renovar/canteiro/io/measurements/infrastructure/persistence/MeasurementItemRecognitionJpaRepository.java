package com.renovar.canteiro.io.measurements.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
interface MeasurementItemRecognitionJpaRepository extends JpaRepository<MeasurementItemRecognitionJpaEntity, UUID> {
    List<MeasurementItemRecognitionJpaEntity> findByMeasurementVersionIdAndCompanyId(UUID versionId, UUID companyId);
    Optional<MeasurementItemRecognitionJpaEntity> findByMeasurementItemIdAndCompanyId(UUID itemId, UUID companyId);
    @Query("select r from MeasurementItemRecognitionJpaEntity r where r.companyId=:company and r.measurementId=:measurement "
        + "and not exists (select newer.id from MeasurementItemRecognitionJpaEntity newer where newer.companyId=r.companyId "
        + "and newer.measurementId=r.measurementId and newer.rootItemId=r.rootItemId and newer.versionNumber>r.versionNumber) "
        + "order by r.versionNumber, r.measurementItemId")
    List<MeasurementItemRecognitionJpaEntity> effective(@Param("company") UUID companyId,@Param("measurement") UUID measurementId);
}

