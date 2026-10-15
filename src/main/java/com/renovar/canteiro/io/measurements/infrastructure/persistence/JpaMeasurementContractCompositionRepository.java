package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementContractCompositionRepository implements MeasurementContractCompositionRepository {
    private final MeasurementContractCompositionJpaRepository repository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    @Override public List<com.renovar.canteiro.io.measurements.domain.MeasurementCompositionFinancialSource> findActiveFinancialSources(UUID mid,UUID company) {
        List<?> rows=entityManager.createNativeQuery("select c.id,c.measurement_version_id,c.measurement_service_id,c.net_amount "
            +"from measurement_contract_composition c where c.company_id=:company and c.measurement_id=:mid "
            +"and not exists(select 1 from measurement_composition_adjustment a where a.company_id=c.company_id and a.composition_id=c.id) "
            +"order by c.created_at,c.id").setParameter("company",company).setParameter("mid",mid).getResultList();
        return rows.stream().map(value->{Object[] row=(Object[])value;return new com.renovar.canteiro.io.measurements.domain.MeasurementCompositionFinancialSource(
            (UUID)row[0],(UUID)row[1],(UUID)row[2],(java.math.BigDecimal)row[3]);}).toList();
    }
    @Override public MeasurementContractComposition save(MeasurementContractComposition composition) {
        if (composition.getId() != null) throw new IllegalStateException("Measurement contract composition is immutable");
        return toDomain(repository.save(new MeasurementContractCompositionJpaEntity(composition)));
    }
    @Override public Optional<MeasurementContractComposition> findByIdAndCompanyId(UUID id, UUID companyId) {
        return repository.findByIdAndCompanyId(id, companyId).map(this::toDomain);
    }
    @Override public Optional<MeasurementContractComposition> findByMeasurementServiceIdAndCompanyId(UUID serviceId, UUID companyId) {
        return repository.findByMeasurementServiceIdAndCompanyId(serviceId, companyId).map(this::toDomain);
    }
    @Override public List<MeasurementContractComposition> findByContractIdAndCompanyId(UUID contractId, UUID companyId) {
        return repository.findByContractIdAndCompanyId(contractId, companyId).stream().map(this::toDomain).toList();
    }
    @Override public List<MeasurementContractComposition> findByMeasurementIdAndCompanyId(UUID measurementId, UUID companyId) {
        return repository.findByMeasurementIdAndCompanyId(measurementId, companyId).stream().map(this::toDomain).toList();
    }
    private MeasurementContractComposition toDomain(MeasurementContractCompositionJpaEntity entity) {
        return MeasurementContractComposition.rehydrate(entity.getId(), entity.getCompanyId(), entity.getMeasurementId(),
                entity.getMeasurementVersionId(), entity.getMeasurementServiceId(), entity.getContractId(), entity.getContractServiceId(),
                entity.getOriginType(), entity.getGrossAmount(), entity.getNetAmount(), entity.getTotalWeightKg(), entity.getItemCount(),
                entity.getMeasurementItemIds(), entity.getJustification(), entity.getCreatedByUserId(), entity.getCreatedAt());
    }
}
