package com.renovar.canteiro.io.payables.infrastructure;

import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.renovar.canteiro.io.payables.domain.PayableFilter;
import com.renovar.canteiro.io.payables.domain.PayableStatus;
import jakarta.persistence.criteria.Predicate;

@Repository
@RequiredArgsConstructor
public class JpaPayableRepository implements PayableRepository {

    private final PayableJpaRepository payableJpaRepository;
    private final JdbcTemplate jdbc;

    @Override
    public Payable save(Payable payable) {
        if(payable.getId()!=null)throw new IllegalArgumentException("Use versioned status update for existing payables");
        UUID group=payable.getFinancialGroupId()==null?createGroup(payable.getCompanyId(),null,null):payable.getFinancialGroupId();
        return toDomain(payableJpaRepository.saveAndFlush(new PayableJpaEntity(payable,group)));
    }

    @Override
    public Optional<Payable> findByTaxObligationIdAndCompanyId(UUID taxObligationId, UUID companyId) {
        return payableJpaRepository.findByTaxObligationIdAndCompanyId(taxObligationId, companyId).map(this::toDomain);
    }

    @Override
    public Optional<Payable> findByTaxObligationIdAndCompanyIdWithLock(UUID taxObligationId, UUID companyId) {
        return payableJpaRepository.findByTaxObligationIdAndCompanyIdWithLock(taxObligationId, companyId).map(this::toDomain);
    }

    @Override
    public Payable updateStatus(Payable payable) {
        PayableJpaEntity entity = payableJpaRepository.findByIdAndCompanyId(payable.getId(),payable.getCompanyId()).orElseThrow();
        if(entity.getVersion()!=payable.getVersion())throw new org.springframework.dao.OptimisticLockingFailureException("Payable version changed");
        entity.setStatus(payable.getStatus());
        return toDomain(payableJpaRepository.saveAndFlush(entity));
    }

    private Payable toDomain(PayableJpaEntity entity) {
        return Payable.restore(
                entity.getId(), entity.getCompanyId(), entity.getFinancialScope(), entity.getWorkId(), entity.getContractId(),
                entity.getTaxObligationId(), entity.getReference(), entity.getDescription(), entity.getDueOn(),
                entity.getOriginalAmount(), entity.getStatus(), entity.getCreatedAt(), entity.getUpdatedAt(),
                entity.getFinancialGroupId(),entity.getSupplierId(),entity.getFinancialCategoryId(),entity.getIssuedOn(),
                entity.getCurrency(),entity.getVersion()
        );
    }

    @Override public Optional<Payable> findByIdAndCompanyId(UUID id,UUID companyId){return payableJpaRepository.findByIdAndCompanyId(id,companyId).map(this::toDomain);}
    @Override public Optional<Payable> findByIdAndCompanyIdWithLock(UUID id, UUID companyId) {
        return payableJpaRepository.findByIdAndCompanyIdWithLock(id, companyId).map(this::toDomain);
    }
    @Override public List<Payable> findByGroup(UUID companyId,UUID groupId){return payableJpaRepository.findByCompanyIdAndFinancialGroupIdOrderByCreatedAtAscIdAsc(companyId,groupId).stream().map(this::toDomain).toList();}
    @Override public Page<Payable> find(UUID companyId,PayableFilter f,Pageable page){
        return payableJpaRepository.findAll((root,query,cb)->{
            List<Predicate> p=new ArrayList<>();p.add(cb.equal(root.get("companyId"),companyId));
            Object[][] fields={{"financialScope",f.financialScope()},{"workId",f.workId()},{"contractId",f.contractId()},
                    {"supplierId",f.supplierId()},{"financialCategoryId",f.financialCategoryId()},{"financialGroupId",f.financialGroupId()},
                    {"currency",f.currency()},{"status",f.status()}};
            for(Object[] field:fields)if(field[1]!=null)p.add(cb.equal(root.get((String)field[0]),field[1]));
            if (f.view() != null) {
                p.add(root.<PayableStatus>get("status").in(f.view().statuses()));
            }
            if(f.reference()!=null){String escaped=f.reference().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_");p.add(cb.like(cb.lower(root.get("reference")),"%"+escaped+"%",'!'));}
            if(f.dueFrom()!=null)p.add(cb.greaterThanOrEqualTo(root.get("dueOn"),f.dueFrom()));
            if(f.dueTo()!=null)p.add(cb.lessThanOrEqualTo(root.get("dueOn"),f.dueTo()));
            return cb.and(p.toArray(Predicate[]::new));
        },page).map(this::toDomain);
    }
    @Override public void lockCreation(UUID companyId,UUID operationId){
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",rs->{},"PAYABLE:"+companyId+":"+operationId);
    }
    @Override public void lockCatalogReferences(UUID companyId,UUID supplierId,UUID categoryId){
        if(supplierId!=null)jdbc.query("select id from supplier where company_id=? and id=? for share",rs->{},companyId,supplierId);
        if(categoryId!=null)jdbc.query("select id from financial_category where company_id=? and id=? for share",rs->{},companyId,categoryId);
    }
    @Override public Optional<CreationGroup> findCreation(UUID companyId,UUID operationId){
        return jdbc.query("select id,payload_hash from payable_financial_group where company_id=? and operation_id=?",
                (rs,n)->new CreationGroup(rs.getObject("id",UUID.class),rs.getString("payload_hash")),companyId,operationId).stream().findFirst();
    }
    @Override public UUID createGroup(UUID companyId,UUID operationId,String hash){
        UUID id=UUID.randomUUID();jdbc.update("insert into payable_financial_group(id,company_id,operation_id,payload_hash) values(?,?,?,?)",id,companyId,operationId,hash);return id;
    }
}
