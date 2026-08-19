package com.renovar.canteiro.io.measurements.domain;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface MeasurementItemContractServiceConversionRepository {

    MeasurementItemContractServiceConversion save(MeasurementItemContractServiceConversion conversion);

    Optional<MeasurementItemContractServiceConversion> findByMeasurementItemIdAndCompanyId(UUID measurementItemId,
                                                                                             UUID companyId);

    List<MeasurementItemContractServiceConversion> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                            UUID companyId);
}
