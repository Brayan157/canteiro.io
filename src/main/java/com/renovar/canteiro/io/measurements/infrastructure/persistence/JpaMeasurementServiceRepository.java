package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementService;
import com.renovar.canteiro.io.measurements.domain.MeasurementServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementServiceRepository implements MeasurementServiceRepository {
    private final MeasurementServiceJpaRepository repository;

    @Override public MeasurementService save(MeasurementService service) {
        if (service.getId() != null) return repository.findByIdAndCompanyId(service.getId(), service.getCompanyId())
                .map(this::toDomain).orElseThrow(() -> new IllegalArgumentException("Measurement service does not exist"));
        return toDomain(repository.save(new MeasurementServiceJpaEntity(service)));
    }
    @Override public Optional<MeasurementService> findByIdAndCompanyId(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }
    @Override public List<MeasurementService> findByMeasurementVersionIdAndCompanyId(UUID versionId, UUID companyId) {
        return repository.findByMeasurementVersionIdAndCompanyIdOrderBySortOrderAsc(versionId, companyId).stream().map(this::toDomain).toList();
    }
    private MeasurementService toDomain(MeasurementServiceJpaEntity entity) {
        return MeasurementService.rehydrate(entity.getId(), entity.getCompanyId(), entity.getMeasurementVersionId(),
                entity.getServiceTitle(), entity.getSortOrder(), entity.getCreatedAt());
    }
}
