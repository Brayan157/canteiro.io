package com.renovar.canteiro.io.measurements.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeasurementVersionRepository {

    MeasurementVersion save(MeasurementVersion measurementVersion);

    Optional<MeasurementVersion> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<MeasurementVersion> findWithLockByIdAndCompanyId(UUID id, UUID companyId);

    Optional<MeasurementVersion> findLatestByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId);
}
