package com.renovar.canteiro.io.measurements.domain;

import java.util.Optional;
import java.util.UUID;

public interface MeasurementDiscountRepository {

    MeasurementDiscount save(MeasurementDiscount measurementDiscount);

    Optional<MeasurementDiscount> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);
}
