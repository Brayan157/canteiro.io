package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.NetBillingRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class JpaNetBillingRepository implements NetBillingRepository {
    @PersistenceContext private EntityManager entityManager;

    @Override public void lockOperation(UUID company, UUID operation) {
        entityManager.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key", "net-billing/" + company + "/" + operation).getResultList();
    }

    @Override public Optional<Operation> findOperation(UUID company, UUID operation) {
        List<?> rows = entityManager.createNativeQuery("select contract_id,fingerprint,billing_installment_id,change_request_id "
                + "from net_billing_operation where company_id=:company and operation_id=:operation")
                .setParameter("company", company).setParameter("operation", operation).getResultList();
        if (rows.isEmpty()) return Optional.empty();
        Object[] r = (Object[]) rows.getFirst();
        return Optional.of(new Operation((UUID) r[0], (String) r[1], (UUID) r[2], (UUID) r[3]));
    }

    @Override public void remember(UUID company, UUID operation, UUID contract, String fingerprint, UUID installment, UUID request) {
        entityManager.flush();
        entityManager.createNativeQuery("insert into net_billing_operation(company_id,operation_id,contract_id,fingerprint,billing_installment_id,change_request_id) "
                + "values (:company,:operation,:contract,:fingerprint,:installment,:request)")
                .setParameter("company", company).setParameter("operation", operation).setParameter("contract", contract)
                .setParameter("fingerprint", fingerprint).setParameter("installment", installment).setParameter("request", request).executeUpdate();
    }

    @Override public void complete(UUID company, UUID operation, UUID request, UUID installment) {
        entityManager.flush();
        int changed = entityManager.createNativeQuery("update net_billing_operation set billing_installment_id=:installment "
                + "where company_id=:company and operation_id=:operation and change_request_id=:request and billing_installment_id is null")
                .setParameter("company", company).setParameter("operation", operation).setParameter("request", request)
                .setParameter("installment", installment).executeUpdate();
        if (changed != 1) throw new IllegalStateException("Net billing operation is already completed or does not match its approval");
    }

    @Override public Set<UUID> legacyConvertedServiceIds(UUID company, UUID contract) {
        List<?> rows = entityManager.createNativeQuery("select contract_service_id from measurement_item_contract_service_conversion "
                + "where company_id=:company and contract_id=:contract")
                .setParameter("company", company).setParameter("contract", contract).getResultList();
        return rows.stream().map(value -> (UUID) value).collect(Collectors.toUnmodifiableSet());
    }
}
