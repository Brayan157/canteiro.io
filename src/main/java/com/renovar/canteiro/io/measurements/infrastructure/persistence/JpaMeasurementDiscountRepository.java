package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementDiscount;
import com.renovar.canteiro.io.measurements.domain.MeasurementDiscountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementDiscountRepository implements MeasurementDiscountRepository {

    private final MeasurementDiscountJpaRepository measurementDiscountJpaRepository;

    @Override
    public MeasurementDiscount save(MeasurementDiscount measurementDiscount) {
        MeasurementDiscountJpaEntity entity = measurementDiscountJpaRepository.save(new MeasurementDiscountJpaEntity(
                measurementDiscount.getCompanyId(), measurementDiscount.getMeasurementVersionId(),
                measurementDiscount.getDiscountType(), measurementDiscount.getDiscountValue()
        ));
        return toDomain(entity);
    }

    @Override
    public Optional<MeasurementDiscount> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId, UUID companyId) {
        return measurementDiscountJpaRepository.findByMeasurementVersionIdAndCompanyId(measurementVersionId, companyId)
                .map(this::toDomain);
    }

    private MeasurementDiscount toDomain(MeasurementDiscountJpaEntity entity) {
        return MeasurementDiscount.rehydrate(
                entity.getId(), entity.getCompanyId(), entity.getMeasurementVersionId(), entity.getDiscountType(),
                entity.getDiscountValue(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }
}
