package com.renovar.canteiro.io.measurements.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MeasurementItemRepository {

    MeasurementItem save(MeasurementItem measurementItem);

    Optional<MeasurementItem> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<MeasurementItem> findWithLockByIdAndCompanyId(UUID id, UUID companyId);

    List<MeasurementItem> findWithLockByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);

    List<MeasurementItem> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId);

    Page<MeasurementItem> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId,
                                                                  Pageable pageable);

    void flush();
}
