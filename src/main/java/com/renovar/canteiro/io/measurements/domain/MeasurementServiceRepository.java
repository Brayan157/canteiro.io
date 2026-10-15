package com.renovar.canteiro.io.measurements.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementServiceRepository {
    MeasurementService save(MeasurementService service);
    Optional<MeasurementService> findByIdAndCompanyId(UUID id, UUID companyId);
    List<MeasurementService> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);
}
