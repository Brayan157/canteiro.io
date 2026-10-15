package com.renovar.canteiro.io.measurements.domain;

import com.renovar.canteiro.io.measurements.application.MeasurementFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface MeasurementRepository {

    Measurement save(Measurement measurement);

    Optional<Measurement> findByIdAndCompanyId(UUID id, UUID companyId);

    Page<Measurement> findByCompanyId(UUID companyId, MeasurementFilter filter, Pageable pageable);
}
