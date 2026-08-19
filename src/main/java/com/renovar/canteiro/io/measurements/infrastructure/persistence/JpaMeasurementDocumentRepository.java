package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementDocument;
import com.renovar.canteiro.io.measurements.domain.MeasurementDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository @RequiredArgsConstructor
public class JpaMeasurementDocumentRepository implements MeasurementDocumentRepository {
    private final MeasurementDocumentJpaRepository repository;
    public MeasurementDocument save(MeasurementDocument document) { return map(repository.save(new MeasurementDocumentJpaEntity(document))); }
    public Optional<MeasurementDocument> findByIdAndCompanyId(UUID id, UUID companyId) { return repository.findByIdAndCompanyId(id, companyId).map(this::map); }
    private MeasurementDocument map(MeasurementDocumentJpaEntity entity) {
        return MeasurementDocument.rehydrate(entity.getId(), entity.getCompanyId(), entity.getMeasurementId(),
                entity.getMeasurementVersionId(), entity.getDocumentType(), entity.getOriginalFilename(), entity.getContentType(),
                entity.getContentSize(), entity.getSha256(), entity.getStorageKey(), entity.getUploadedByUserId(), entity.getCreatedAt());
    }
}
