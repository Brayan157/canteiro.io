package com.renovar.canteiro.io.measurements.infrastructure.persistence;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuityRepository;
import com.renovar.canteiro.io.measurements.domain.MeasurementContinuousService;
import com.renovar.canteiro.io.measurements.domain.MeasurementItemRecognition;
import com.renovar.canteiro.io.measurements.domain.MeasurementRecognitionTotals;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
@Repository @RequiredArgsConstructor
public class JpaMeasurementContinuityRepository implements MeasurementContinuityRepository {
    private final MeasurementContinuousServiceJpaRepository services;
    private final MeasurementItemRecognitionJpaRepository recognitions;
    @PersistenceContext private EntityManager em;
    @Override public void lockMeasurement(UUID company, UUID mid) {
        em.flush();
        em.createNativeQuery("select id from measurement where company_id=:company and id=:mid for update")
                .setParameter("company",company).setParameter("mid",mid).getResultList();
    }
    @Override public MeasurementContinuousService save(MeasurementContinuousService s) {
        MeasurementContinuousServiceJpaEntity entity;
        if(s.id()==null) entity=new MeasurementContinuousServiceJpaEntity(s);
        else {
            entity=services.findByIdAndCompanyId(s.id(),s.companyId()).orElseThrow();
            if(entity.getLockVersion()!=s.lockVersion()) throw new ObjectOptimisticLockingFailureException(MeasurementContinuousService.class,s.id());
            entity.destination(s);
        }
        entity=services.saveAndFlush(entity);
        return domain(entity);
    }
    @Override public Optional<MeasurementContinuousService> findService(UUID company,UUID id) {
        return services.findByIdAndCompanyId(id,company).map(this::domain);
    }
    @Override public List<MeasurementContinuousService> findServices(UUID company,UUID mid) {
        return services.findByMeasurementIdAndCompanyIdOrderBySortOrderAscIdAsc(mid,company).stream().map(this::domain).toList();
    }
    @Override public void bindItem(UUID company,UUID mid,UUID version,UUID item,UUID service) {
        em.flush();
        em.createNativeQuery("insert into measurement_item_service (measurement_item_id,company_id,measurement_id,measurement_version_id,continuous_service_id) "
            +"values (:item,:company,:mid,:version,:service) on conflict (measurement_item_id,company_id) do update "
            +"set continuous_service_id=excluded.continuous_service_id")
            .setParameter("item",item).setParameter("company",company).setParameter("mid",mid).setParameter("version",version).setParameter("service",service).executeUpdate();
    }
    @Override public Optional<UUID> serviceForItem(UUID company,UUID item) {
        return uuid("select continuous_service_id from measurement_item_service where company_id=:company and measurement_item_id=:key",company,item);
    }
    @Override public java.util.Map<UUID,UUID> serviceBindings(UUID company,List<UUID> items) {
        if(items.isEmpty())return java.util.Map.of();
        List<?> rows=em.createNativeQuery("select measurement_item_id,continuous_service_id from measurement_item_service where company_id=:company and measurement_item_id in (:items)")
            .setParameter("company",company).setParameter("items",items).getResultList();
        java.util.Map<UUID,UUID> result=new java.util.HashMap<>();
        for(Object row:rows){Object[] pair=(Object[])row;result.put((UUID)pair[0],(UUID)pair[1]);}return result;
    }
    @Override public void inheritItemBinding(UUID company,UUID source,UUID item,UUID version) {
        em.flush();
        em.createNativeQuery("insert into measurement_item_service (measurement_item_id,company_id,measurement_id,measurement_version_id,continuous_service_id) "
            +"select :item,company_id,measurement_id,:version,continuous_service_id from measurement_item_service "
            +"where company_id=:company and measurement_item_id=:source on conflict do nothing")
            .setParameter("item",item).setParameter("company",company).setParameter("source",source).setParameter("version",version).executeUpdate();
    }
    @Override public void bindGroup(UUID company,UUID group,UUID service) {
        em.flush();
        em.createNativeQuery("update measurement_service set continuous_service_id=:service where company_id=:company and id=:group")
            .setParameter("service",service).setParameter("company",company).setParameter("group",group).executeUpdate();
    }
    @Override public Optional<UUID> serviceForGroup(UUID company,UUID group) {
        return uuid("select continuous_service_id from measurement_service where company_id=:company and id=:key",company,group);
    }
    @Override public void saveRecognition(MeasurementItemRecognition r) {
        if(recognitions.findByMeasurementItemIdAndCompanyId(r.measurementItemId(),r.companyId()).isEmpty())
            recognitions.saveAndFlush(new MeasurementItemRecognitionJpaEntity(r));
    }
    @Override public List<MeasurementItemRecognition> recognitionsForVersion(UUID company,UUID version) {
        return recognitions.findByMeasurementVersionIdAndCompanyId(version,company).stream().map(MeasurementItemRecognitionJpaEntity::domain).toList();
    }
    @Override public List<MeasurementItemRecognition> effectiveRecognitions(UUID company,UUID mid) {
        return recognitions.effective(company,mid).stream().map(MeasurementItemRecognitionJpaEntity::domain).toList();
    }
    @Override public List<MeasurementRecognitionTotals> effectiveTotals(UUID company,UUID mid) {
        List<?> rows=em.createNativeQuery("select r.continuous_service_id,count(*),sum(r.gross_amount),sum(r.net_amount),sum(r.total_weight_kg) "
            +"from measurement_item_recognition r where r.company_id=:company and r.measurement_id=:mid and r.active=true "
            +"and not exists(select 1 from measurement_item_recognition n where n.company_id=r.company_id and n.measurement_id=r.measurement_id "
            +"and n.root_item_id=r.root_item_id and n.version_number>r.version_number) group by r.continuous_service_id")
            .setParameter("company",company).setParameter("mid",mid).getResultList();
        return rows.stream().map(value->{Object[] r=(Object[])value;return new MeasurementRecognitionTotals((UUID)r[0],((Number)r[1]).longValue(),
            (BigDecimal)r[2],(BigDecimal)r[3],(BigDecimal)r[4]);}).toList();
    }
    @Override public List<MeasurementItemRecognition> effectivePage(UUID company,UUID mid,UUID sid,int offset,int limit) {
        List<?> rows=em.createNativeQuery("select r.* from measurement_item_recognition r join measurement_item i on i.id=r.measurement_item_id and i.company_id=r.company_id "
            +"where r.company_id=:company and r.measurement_id=:mid and r.continuous_service_id=:sid and r.active=true "
            +"and not exists(select 1 from measurement_item_recognition n where n.company_id=r.company_id and n.measurement_id=r.measurement_id "
            +"and n.root_item_id=r.root_item_id and n.version_number>r.version_number) order by r.version_number,i.item_number,r.measurement_item_id",
            MeasurementItemRecognitionJpaEntity.class).setParameter("company",company).setParameter("mid",mid).setParameter("sid",sid)
            .setFirstResult(offset).setMaxResults(limit).getResultList();
        return rows.stream().map(row->((MeasurementItemRecognitionJpaEntity)row).domain()).toList();
    }
    @Override public List<UUID> pendingItemIdsPage(UUID company,UUID version,UUID sid,int offset,int limit) {
        String condition=sid==null?"b.continuous_service_id is null":"b.continuous_service_id=:sid";
        var query=em.createNativeQuery("select i.id from measurement_item i left join measurement_item_service b "
            +"on b.measurement_item_id=i.id and b.company_id=i.company_id where i.company_id=:company and i.measurement_version_id=:version and "
            +condition+" order by i.item_number,i.id").setParameter("company",company).setParameter("version",version)
            .setFirstResult(offset).setMaxResults(limit);
        if(sid!=null)query.setParameter("sid",sid);
        return query.getResultList().stream().map(id->(UUID)id).toList();
    }
    @Override public void clearReadContext() { em.clear(); }
    @Override public Optional<MeasurementItemRecognition> recognitionForItem(UUID company,UUID item) {
        return recognitions.findByMeasurementItemIdAndCompanyId(item,company).map(MeasurementItemRecognitionJpaEntity::domain);
    }
    @Override public void reverseComposition(UUID company,UUID cid,UUID version,BigDecimal amount,String reason,UUID actor) {
        em.flush();
        em.createNativeQuery("insert into measurement_composition_adjustment (id,company_id,composition_id,corrective_version_id,net_amount,reason,actor_user_id) "
            +"values (:id,:company,:cid,:version,:amount,:reason,:actor) on conflict (company_id,composition_id) do nothing")
            .setParameter("id",UUID.randomUUID()).setParameter("company",company).setParameter("cid",cid).setParameter("version",version)
            .setParameter("amount",amount.negate()).setParameter("reason",reason).setParameter("actor",actor).executeUpdate();
    }
    @Override public boolean isReversed(UUID company,UUID cid) {
        return uuid("select id from measurement_composition_adjustment where company_id=:company and composition_id=:key",company,cid).isPresent();
    }
    @Override public boolean hasUnreconciledReduction(UUID company,UUID cid) {
        // A reduced accepted origin cannot be billed against its obsolete contractual position
        // while the independent SERVICES authorization is still pending.
        Number count=(Number)em.createNativeQuery("select count(*) from measurement_contract_composition c "
            +"join measurement_service g on g.id=c.measurement_service_id and g.company_id=c.company_id "
            +"where c.company_id=:company and c.id=:cid and "
            +"coalesce((select sum(r.net_amount) from measurement_item_recognition r where r.company_id=c.company_id "
            +"and r.continuous_service_id=g.continuous_service_id and r.active=true and not exists "
            +"(select 1 from measurement_item_recognition n where n.company_id=r.company_id and n.root_item_id=r.root_item_id and n.version_number>r.version_number)),0) "
            +"< (select coalesce(sum(p.net_amount),0) from measurement_contract_composition p "
            +"join measurement_service pg on pg.id=p.measurement_service_id and pg.company_id=p.company_id "
            +"where p.company_id=c.company_id and pg.continuous_service_id=g.continuous_service_id "
            +"and not exists(select 1 from measurement_composition_adjustment a where a.company_id=p.company_id and a.composition_id=p.id))")
            .setParameter("company",company).setParameter("cid",cid).getSingleResult();
        return count.longValue()>0;
    }
    @Override public Optional<UUID> pendingCompositionRequest(UUID company,UUID group) {
        return uuid("select l.change_request_id from measurement_composition_request l join change_request c on c.id=l.change_request_id and c.company_id=l.company_id "
            +"where l.company_id=:company and l.measurement_service_id=:key and c.status='PENDING'",company,group);
    }
    @Override public void rememberCompositionRequest(UUID company,UUID group,UUID request) {
        em.flush();
        em.createNativeQuery("insert into measurement_composition_request (company_id,measurement_service_id,change_request_id) values (:company,:group,:request) "
            +"on conflict (company_id,measurement_service_id) do update set change_request_id=excluded.change_request_id")
            .setParameter("company",company).setParameter("group",group).setParameter("request",request).executeUpdate();
    }
    private Optional<UUID> uuid(String sql,UUID company,UUID key) {
        List<?> rows=em.createNativeQuery(sql).setParameter("company",company).setParameter("key",key).getResultList();
        return rows.isEmpty()||rows.getFirst()==null ? Optional.empty() : Optional.of((UUID)rows.getFirst());
    }
    private MeasurementContinuousService domain(MeasurementContinuousServiceJpaEntity s) {
        return new MeasurementContinuousService(s.getId(),s.getCompanyId(),s.getMeasurementId(),s.getServiceTitle(),s.getSortOrder(),
            s.getDestinationAction(),s.getContractServiceId(),s.getLockVersion(),s.getCreatedAt(),s.getUpdatedAt());
    }
}
