package com.renovar.canteiro.io.measurements.domain;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementContractAdjustmentRepository {

    MeasurementContractAdjustment save(MeasurementContractAdjustment adjustment);

    Optional<MeasurementContractAdjustment> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                     UUID companyId);

    BigDecimal sumByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
