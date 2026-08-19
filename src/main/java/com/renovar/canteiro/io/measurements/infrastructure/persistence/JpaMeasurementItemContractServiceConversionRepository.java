package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversion;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemContractServiceConversionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementItemContractServiceConversionRepository
        implements MeasurementItemContractServiceConversionRepository {

    private final MeasurementItemContractServiceConversionJpaRepository repository;

    @Override
    public MeasurementItemContractServiceConversion save(MeasurementItemContractServiceConversion conversion) {
        return map(repository.save(new MeasurementItemContractServiceConversionJpaEntity(conversion)));
    }

    @Override
    public Optional<MeasurementItemContractServiceConversion> findByMeasurementItemIdAndCompanyId(UUID measurementItemId,
                                                                                                     UUID companyId) {
        return repository.findByMeasurementItemIdAndCompanyId(measurementItemId, companyId).map(this::map);
    }

    @Override
    public List<MeasurementItemContractServiceConversion> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                                    UUID companyId) {
        return repository.findByMeasurementVersionIdAndCompanyId(measurementVersionId, companyId).stream()
                .map(this::map)
                .toList();
    }

    private MeasurementItemContractServiceConversion map(MeasurementItemContractServiceConversionJpaEntity entity) {
        return MeasurementItemContractServiceConversion.rehydrate(entity.getId(), entity.getCompanyId(),
                entity.getMeasurementVersionId(), entity.getMeasurementItemId(), entity.getContractId(),
                entity.getContractServiceId(), entity.getCreatedAt());
    }
}
