package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementItem;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementItemRepository implements MeasurementItemRepository {

    private final MeasurementItemJpaRepository measurementItemJpaRepository;

    @Override
    public MeasurementItem save(MeasurementItem measurementItem) {
        MeasurementItemJpaEntity entity = new MeasurementItemJpaEntity(
                measurementItem.getCompanyId(), measurementItem.getMeasurementVersionId(), measurementItem.getItemNumber(),
                measurementItem.getActivity(), measurementItem.getDescription(), measurementItem.getChargeType(),
                measurementItem.getAreaSquareMeters(), measurementItem.getLinearMeters(),
                measurementItem.getKilogramsPerSquareMeter(), measurementItem.getKilogramsPerLinearMeter(),
                measurementItem.getUnitPrice(), measurementItem.getTotalWeightKg(), measurementItem.getTotalAmount(),
                measurementItem.getCalculationFormula()
        );
        return toDomain(measurementItemJpaRepository.save(entity));
    }

    @Override
    public Optional<MeasurementItem> findByIdAndCompanyId(UUID id, UUID companyId) {
        return measurementItemJpaRepository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public Optional<MeasurementItem> findWithLockByIdAndCompanyId(UUID id, UUID companyId) {
        return measurementItemJpaRepository.findWithLockByIdAndCompanyId(id, companyId).map(this::toDomain);
    }

    @Override
    public List<MeasurementItem> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId) {
        return measurementItemJpaRepository.findByMeasurementVersionIdAndCompanyId(measurementVersionId, companyId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Page<MeasurementItem> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId,
                                                                          Pageable pageable) {
        return measurementItemJpaRepository.findByMeasurementVersionIdAndCompanyId(measurementVersionId, companyId, pageable)
                .map(this::toDomain);
    }

    private MeasurementItem toDomain(MeasurementItemJpaEntity entity) {
        return MeasurementItem.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getMeasurementVersionId(), entity.getItemNumber(),
                entity.getActivity(), entity.getDescription(), entity.getChargeType(), entity.getAreaSquareMeters(),
                entity.getLinearMeters(), entity.getKilogramsPerSquareMeter(), entity.getKilogramsPerLinearMeter(),
                entity.getUnitPrice(), entity.getTotalWeightKg(), entity.getTotalAmount(), entity.getCalculationFormula(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
