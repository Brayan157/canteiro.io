package com.renovar.canteiro.io.measurements.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeasurementRepository {

    Measurement save(Measurement measurement);

    Optional<Measurement> findByIdAndCompanyId(UUID id, UUID companyId);
}
