package com.renovar.canteiro.io.receivables.infrastructure.persistence;

import com.renovar.canteiro.io.receivables.domain.InvoiceTaxOperationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxCalculation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaInvoiceTaxOperationRepository implements InvoiceTaxOperationRepository {
    @PersistenceContext private EntityManager em;
    @Override public void lock(UUID company, UUID operation) {
        em.createNativeQuery("select 1 from pg_advisory_xact_lock(hashtextextended(:key,0))")
                .setParameter("key", "invoice-tax/" + company + "/" + operation).getResultList();
    }
    @Override public Optional<Operation> find(UUID company, UUID operation) { return read(company, operation, null); }
    @Override public Optional<Operation> findByTax(UUID company, UUID tax) { return read(company, null, tax); }
    private Optional<Operation> read(UUID company, UUID operation, UUID tax) {
        var query = em.createNativeQuery("select operation_id,invoice_id,fingerprint,calculation_mode,base_amount,rate_percent,amount,tax_obligation_id,change_request_id from invoice_tax_operation where company_id=:company and "
                + (tax == null ? "operation_id=:id" : "tax_obligation_id=:id")).setParameter("company", company).setParameter("id", tax == null ? operation : tax);
        List<?> rows = query.getResultList();
        return rows.stream().findFirst().map(value -> { Object[] r = (Object[]) value;
            return new Operation((UUID) r[0], (UUID) r[1], (String) r[2], new TaxCalculation(TaxCalculation.Mode.valueOf((String) r[3]),
                    (BigDecimal) r[4], (BigDecimal) r[5], (BigDecimal) r[6]), (UUID) r[7], (UUID) r[8]); });
    }
    @Override public void remember(UUID company, UUID operation, UUID invoice, UUID contract, String fingerprint, TaxCalculation c, UUID tax, UUID request) {
        em.flush();
        em.createNativeQuery("insert into invoice_tax_operation(company_id,operation_id,invoice_id,contract_id,fingerprint,calculation_mode,base_amount,rate_percent,amount,tax_obligation_id,change_request_id) values (:company,:operation,:invoice,:contract,:hash,:mode,:base,:rate,:amount,:tax,:request)")
                .setParameter("company", company).setParameter("operation", operation).setParameter("invoice", invoice).setParameter("contract", contract)
                .setParameter("hash", fingerprint).setParameter("mode", c.mode().name()).setParameter("base", c.baseAmount()).setParameter("rate", c.ratePercent())
                .setParameter("amount", c.amount()).setParameter("tax", tax).setParameter("request", request).executeUpdate();
    }
    @Override public void complete(UUID company, UUID operation, UUID request, UUID tax) {
        em.flush();
        int changed = em.createNativeQuery("update invoice_tax_operation set tax_obligation_id=:tax where company_id=:company and operation_id=:operation and change_request_id=:request and tax_obligation_id is null")
                .setParameter("tax", tax).setParameter("company", company).setParameter("operation", operation).setParameter("request", request).executeUpdate();
        if (changed != 1) throw new IllegalStateException("Tax operation no longer matches its pending approval");
    }
}
