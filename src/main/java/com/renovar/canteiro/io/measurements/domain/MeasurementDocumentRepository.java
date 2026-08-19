package com.renovar.canteiro.io.measurements.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeasurementDocumentRepository {
    MeasurementDocument save(MeasurementDocument document);
    Optional<MeasurementDocument> findByIdAndCompanyId(UUID id, UUID companyId);
}
