package com.renovar.canteiro.io.measurements.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementContractCompositionRepository {
    MeasurementContractComposition save(MeasurementContractComposition composition);
    Optional<MeasurementContractComposition> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<MeasurementContractComposition> findByMeasurementServiceIdAndCompanyId(UUID measurementServiceId, UUID companyId);
    List<MeasurementContractComposition> findByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId);
    List<MeasurementCompositionFinancialSource> findActiveFinancialSources(UUID measurementId,UUID companyId);
    List<MeasurementContractComposition> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
