package com.renovar.canteiro.io.measurements.infrastructure.persistence;

import com.renovar.canteiro.io.measurements.domain.MeasurementContractComposition;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository;
import com.renovar.canteiro.io.measurements.domain.BillableMeasurementOrigin;
import com.renovar.canteiro.io.measurements.domain.MeasurementCompositionOriginType;
import com.renovar.canteiro.io.measurements.domain.MeasurementContractFinancialOrigin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class JpaMeasurementContractCompositionRepository implements MeasurementContractCompositionRepository {
    private final MeasurementContractCompositionJpaRepository repository;
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    @Override
    public List<MeasurementContractFinancialOrigin> findActiveContractFinancialOrigins(UUID companyId, UUID contractId) {
        List<?> rows = entityManager.createNativeQuery("""
                select c.id,c.contract_service_id,c.measurement_id,c.measurement_version_id,c.measurement_service_id,
                    g.continuous_service_id,c.net_amount,(v.status='ACCEPTED' and m.status<>'CANCELLED' and not (
                """ + MeasurementCompositionEligibilitySql.UNRECONCILED_REDUCTION + """
                )) as eligible,v.external_acceptance_on from measurement_contract_composition c
                join measurement_service g on g.id=c.measurement_service_id and g.company_id=c.company_id
                join measurement_version v on v.id=c.measurement_version_id and v.company_id=c.company_id
                join measurement m on m.id=c.measurement_id and m.company_id=c.company_id
                where c.company_id=:company and c.contract_id=:contract and not exists (
                    select 1 from measurement_composition_adjustment a where a.company_id=c.company_id and a.composition_id=c.id)
                order by c.id
                """).setParameter("company", companyId).setParameter("contract", contractId).getResultList();
        return rows.stream().map(value -> {
            Object[] r = (Object[]) value;
            return new MeasurementContractFinancialOrigin((UUID) r[0], (UUID) r[1], (UUID) r[2], (UUID) r[3],
                    (UUID) r[4], (UUID) r[5], (BigDecimal) r[6], (Boolean) r[7],
                    r[8] == null ? null : r[8] instanceof java.time.LocalDate d ? d : ((java.sql.Date) r[8]).toLocalDate());
        }).toList();
    }

    @Override
    public Page<BillableMeasurementOrigin> findBillableOrigins(UUID companyId, UUID contractId,
            UUID measurementId, UUID contractServiceId, Pageable pageable) {
        String from = """
                from measurement_contract_composition c
                join measurement m on m.id = c.measurement_id and m.company_id = c.company_id
                join measurement_version v on v.id = c.measurement_version_id
                    and v.measurement_id = m.id and v.company_id = c.company_id
                join measurement_service g on g.id = c.measurement_service_id
                    and g.measurement_version_id = v.id and g.company_id = c.company_id
                join contract_service s on s.id = c.contract_service_id
                    and s.contract_id = c.contract_id and s.company_id = c.company_id
                join lateral (
                    select coalesce(sum(b.allocated_amount), 0) as amount,
                        coalesce(sum(coalesce(b.net_billing_gross_amount,b.allocated_amount)),0) as capacity from billing_allocation b
                    where b.company_id = c.company_id and b.contract_id = c.contract_id
                    and b.contract_service_id = c.contract_service_id
                    and b.measurement_contract_composition_id = c.id and b.status = 'ACTIVE'
                ) allocated on true
                where c.company_id = :company and c.contract_id = :contract
                and v.status = 'ACCEPTED' and m.status <> 'CANCELLED' and s.status <> 'CANCELLED'
                and not exists (select 1 from measurement_composition_adjustment a
                    where a.company_id = c.company_id and a.composition_id = c.id)
                and c.net_amount > allocated.capacity and not (
                """ + MeasurementCompositionEligibilitySql.UNRECONCILED_REDUCTION + ")";
        if (measurementId != null) from += " and c.measurement_id = :measurement";
        if (contractServiceId != null) from += " and c.contract_service_id = :service";
        String select = """
                select c.id, c.contract_id, c.contract_service_id, c.measurement_id,
                    c.measurement_version_id, c.measurement_service_id, g.continuous_service_id,
                    m.reference, m.title, g.service_title, s.name, v.version_number, c.origin_type,
                    c.net_amount, allocated.amount, c.net_amount - allocated.capacity, c.created_at
                """;
        // Sort field identifiers never come from input. Only the validated direction is used.
        var order = pageable.getSort().getOrderFor("createdAt");
        String direction = order != null && order.isAscending() ? "asc" : "desc";
        var rows = entityManager.createNativeQuery(select + from + " order by c.created_at " + direction + ", c.id " + direction);
        var count = entityManager.createNativeQuery("select count(*) " + from);
        for (var query : List.of(rows, count)) {
            query.setParameter("company", companyId).setParameter("contract", contractId);
            if (measurementId != null) query.setParameter("measurement", measurementId);
            if (contractServiceId != null) query.setParameter("service", contractServiceId);
        }
        long total = ((Number) count.getSingleResult()).longValue();
        List<?> values = rows.setFirstResult(Math.toIntExact(pageable.getOffset())).setMaxResults(pageable.getPageSize()).getResultList();
        List<BillableMeasurementOrigin> result = values.stream().map(value -> {
            Object[] r = (Object[]) value;
            return new BillableMeasurementOrigin((UUID) r[0], (UUID) r[1], (UUID) r[2], (UUID) r[3],
                    (UUID) r[4], (UUID) r[5], (UUID) r[6], (String) r[7], (String) r[8], (String) r[9],
                    (String) r[10], ((Number) r[11]).intValue(), MeasurementCompositionOriginType.valueOf((String) r[12]),
                    (BigDecimal) r[13], (BigDecimal) r[14], (BigDecimal) r[15], instant(r[16]));
        }).toList();
        return new PageImpl<>(result, pageable, total);
    }

    private Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof OffsetDateTime dateTime) return dateTime.toInstant();
        return ((java.sql.Timestamp) value).toInstant();
    }
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
