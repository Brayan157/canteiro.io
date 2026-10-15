package com.renovar.canteiro.io.receivables.infrastructure.persistence;
import com.renovar.canteiro.io.receivables.domain.ReceivableCredit;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
@Repository
public class JpaReceivableOriginRepository implements ReceivableOriginRepository {
    @PersistenceContext EntityManager em;
    public void lockInstallment(UUID company,UUID installment){
        em.flush();em.createNativeQuery("select id from billing_installment where company_id=:company and id=:installment for update")
            .setParameter("company",company).setParameter("installment",installment).getResultList();
    }
    public ReceivableCredit saveCredit(ReceivableCredit c){
        UUID id=UUID.randomUUID();em.flush();
        em.createNativeQuery("insert into receivable_credit(id,company_id,receivable_id,contract_id,amount,reason,reversal_of_credit_id,actor_user_id) "
            +"values (:id,:company,:rid,:contract,:amount,:reason,:reversal,:actor)").setParameter("id",id).setParameter("company",c.companyId())
            .setParameter("rid",c.receivableId()).setParameter("contract",c.contractId()).setParameter("amount",c.amount())
            .setParameter("reason",c.reason()).setParameter("reversal",c.reversalOfCreditId()).setParameter("actor",c.actorUserId()).executeUpdate();
        return new ReceivableCredit(id,c.companyId(),c.receivableId(),c.contractId(),c.amount(),c.reason(),c.reversalOfCreditId(),c.actorUserId(),Instant.now());
    }
    public Optional<ReceivableCredit> findCredit(UUID company,UUID id){
        List<?> rows=em.createNativeQuery("select id,company_id,receivable_id,contract_id,amount,reason,reversal_of_credit_id,actor_user_id from receivable_credit where company_id=:company and id=:id")
            .setParameter("company",company).setParameter("id",id).getResultList();
        if(rows.isEmpty())return Optional.empty();Object[] r=(Object[])rows.getFirst();
        return Optional.of(new ReceivableCredit((UUID)r[0],(UUID)r[1],(UUID)r[2],(UUID)r[3],(BigDecimal)r[4],(String)r[5],(UUID)r[6],(UUID)r[7],null));
    }
    public boolean hasCreditReversal(UUID company,UUID id){return ((Number)em.createNativeQuery("select count(*) from receivable_credit where company_id=:company and reversal_of_credit_id=:id").setParameter("company",company).setParameter("id",id).getSingleResult()).longValue()>0;}
    public BigDecimal effectiveCredits(UUID company,UUID rid){
        return (BigDecimal)em.createNativeQuery("select coalesce(sum(case when reversal_of_credit_id is null then amount else -amount end),0) from receivable_credit where company_id=:company and receivable_id=:rid")
            .setParameter("company",company).setParameter("rid",rid).getSingleResult();
    }
    public void attributeSettlement(UUID company,UUID sid,UUID allocation,BigDecimal amount){
        em.flush();em.createNativeQuery("insert into receivable_settlement_allocation(company_id,settlement_id,billing_allocation_id,amount) "
            +"values (:company,:sid,:allocation,:amount) on conflict do nothing").setParameter("company",company).setParameter("sid",sid)
            .setParameter("allocation",allocation).setParameter("amount",amount).executeUpdate();
    }
    public Map<UUID,BigDecimal> settlementAttribution(UUID company,UUID sid){
        return map(em.createNativeQuery("select billing_allocation_id,amount from receivable_settlement_allocation where company_id=:company and settlement_id=:sid")
            .setParameter("company",company).setParameter("sid",sid).getResultList());
    }
    public Map<UUID,BigDecimal> receivedByAllocation(UUID company,UUID installment){
        return map(em.createNativeQuery("select a.billing_allocation_id,sum(a.amount) from receivable_settlement_allocation a "
            +"join receivable_settlement s on s.id=a.settlement_id and s.company_id=a.company_id "
            +"join billing_allocation b on b.id=a.billing_allocation_id and b.company_id=a.company_id "
            +"where a.company_id=:company and b.billing_installment_id=:installment and s.status='CONFIRMED' group by a.billing_allocation_id")
            .setParameter("company",company).setParameter("installment",installment).getResultList());
    }
    private Map<UUID,BigDecimal> map(List<?> rows){Map<UUID,BigDecimal> result=new LinkedHashMap<>();for(Object value:rows){Object[] r=(Object[])value;result.put((UUID)r[0],(BigDecimal)r[1]);}return result;}
}

