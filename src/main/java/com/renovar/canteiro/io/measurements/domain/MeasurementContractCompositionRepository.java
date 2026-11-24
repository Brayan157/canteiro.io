package com.renovar.canteiro.io.measurements.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MeasurementContractCompositionRepository {
    List<MeasurementContractFinancialOrigin> findActiveContractFinancialOrigins(UUID companyId, UUID contractId);
    Page<BillableMeasurementOrigin> findBillableOrigins(
            UUID companyId, UUID contractId, UUID measurementId, UUID contractServiceId,
            Pageable pageable);
    MeasurementContractComposition save(MeasurementContractComposition composition);
    Optional<MeasurementContractComposition> findByIdAndCompanyId(UUID id, UUID companyId);
    Optional<MeasurementContractComposition> findByMeasurementServiceIdAndCompanyId(UUID measurementServiceId, UUID companyId);
    List<MeasurementContractComposition> findByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId);
    List<MeasurementCompositionFinancialSource> findActiveFinancialSources(UUID measurementId,UUID companyId);
    List<MeasurementContractComposition> findByContractIdAndCompanyId(UUID contractId, UUID companyId);
}
