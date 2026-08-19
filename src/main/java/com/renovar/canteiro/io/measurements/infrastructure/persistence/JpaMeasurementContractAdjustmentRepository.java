package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustment;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractAdjustmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementContractAdjustmentRepository implements MeasurementContractAdjustmentRepository {

    private final MeasurementContractAdjustmentJpaRepository repository;

    @Override
    public MeasurementContractAdjustment save(MeasurementContractAdjustment adjustment) {
        return map(repository.save(new MeasurementContractAdjustmentJpaEntity(adjustment)));
    }

    @Override
    public Optional<MeasurementContractAdjustment> findByMeasurementVersionIdAndCompanyId(UUID measurementVersionId,
                                                                                            UUID companyId) {
        return repository.findByMeasurementVersionIdAndCompanyId(measurementVersionId, companyId).map(this::map);
    }

    @Override
    public BigDecimal sumByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return repository.sumByContractIdAndCompanyId(contractId, companyId);
    }

    private MeasurementContractAdjustment map(MeasurementContractAdjustmentJpaEntity entity) {
        return MeasurementContractAdjustment.rehydrate(entity.getId(), entity.getCompanyId(),
                entity.getMeasurementVersionId(), entity.getContractId(), entity.getAdjustmentAmount(), entity.getCreatedAt());
    }
}
