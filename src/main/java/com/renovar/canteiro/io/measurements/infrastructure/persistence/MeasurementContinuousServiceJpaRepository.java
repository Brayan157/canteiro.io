package com.renovar.canteiro.io.measurements.infrastructure.persistence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
interface MeasurementContinuousServiceJpaRepository extends JpaRepository<MeasurementContinuousServiceJpaEntity, UUID> {
    @Query(value = """
        SELECT s.* FROM measurement_continuous_service s
        WHERE s.id = :id AND s.company_id = :company
          AND NOT EXISTS (SELECT 1 FROM measurement_continuous_service_retirement r
              WHERE r.company_id = s.company_id AND r.continuous_service_id = s.id)
        """, nativeQuery = true)
    Optional<MeasurementContinuousServiceJpaEntity> findByIdAndCompanyId(@Param("id") UUID id, @Param("company") UUID companyId);

    @Query(value = """
        SELECT s.* FROM measurement_continuous_service s
        WHERE s.measurement_id = :measurement AND s.company_id = :company
          AND NOT EXISTS (SELECT 1 FROM measurement_continuous_service_retirement r
              WHERE r.company_id = s.company_id AND r.continuous_service_id = s.id)
        ORDER BY s.sort_order, s.id
        """, nativeQuery = true)
    List<MeasurementContinuousServiceJpaEntity> findByMeasurementIdAndCompanyIdOrderBySortOrderAscIdAsc(
        @Param("measurement") UUID measurementId, @Param("company") UUID companyId);
}

