package com.renovar.canteiro.io.receivables;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.contracts.application.ContractClosureFinancialProvider;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.Approval;
import com.renovar.canteiro.io.receivables.application.HistoricalReceiptLinkPlan.Entry;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.receivables.infrastructure.maintenance.HistoricalReceiptLinkMaintenance;
import com.renovar.canteiro.io.receivables.infrastructure.maintenance.HistoricalReceiptLinkMaintenanceCommand.Options;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoricalReceiptLinkMaintenanceIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired CompanyRepository companies;
    @Autowired FinalCustomerRepository customers;
    @Autowired WorkRepository works;
    @Autowired ContractRepository contracts;
    @Autowired ContractServiceRepository services;
    @Autowired ContractDiscountRepository discounts;
    @Autowired BillingInstallmentRepository installments;
    @Autowired BillingAllocationRepository allocations;
    @Autowired ReceivableRepository titles;
    @Autowired ReceivableSettlementRepository settlements;
    @Autowired ReceivableOriginRepository origins;
    @Autowired InvoiceRepository invoices;
    @Autowired InvoiceItemRepository items;
    @Autowired TaxObligationRepository taxes;
    @Autowired ContractClosureFinancialProvider financial;

    @Test void validationIsReadOnlyAndDoesNotAuthorizeApplication() {
        var plan=fixture();var before=snapshot(plan,true);var job=job();
        var preview=job.validate(plan);
        assertEquals("VALIDATED_ONLY",preview.status());assertFalse(preview.alreadyApplied());
        assertEquals(plan.fingerprint(),preview.planSha256());assertEquals(preview,job.validate(plan));
        assertEquals(before,snapshot(plan,true));assertEquals(0,count("invoice_settlement_line",plan));
        assertThrows(IllegalStateException.class,()->job.apply(plan,null));
        assertEquals(before,snapshot(plan,true));
    }

    @Test void appliesOnlyLinksAndSystemAuditThenReplaysWithoutAnotherReceiptOrForcedClosure() {
        var plan=fixture();var job=job();var preview=job.validate(plan);var before=snapshot(plan,false);
        var service=services.findByIdAndCompanyId(plan.serviceId(),plan.companyId()).orElseThrow();
        assertEquals("SERVICE_FINANCIAL_RECONCILIATION_REQUIRED",financial.positions(plan.companyId(),plan.contractId(),List.of(service)).get(plan.serviceId()).completionReason());
        var result=job.apply(plan,approval(preview));assertEquals("APPLIED",result.status());
        assertEquals(before,snapshot(plan,false));assertEquals(2,count("invoice_settlement_line",plan));
        assertEquals(2,count("receivable_settlement",plan));
        assertNull(financial.positions(plan.companyId(),plan.contractId(),List.of(service)).get(plan.serviceId()).completionReason());
        var audit=jdbc.queryForMap("select actor_type,actor_user_id,origin,after_data::text payload,metadata->>'approvalReference' approval from audit_event where company_id=? and id=?",plan.companyId(),plan.operationId());
        assertEquals("SYSTEM",audit.get("actor_type"));assertNull(audit.get("actor_user_id"));assertEquals("system",audit.get("origin"));
        assertEquals("synthetic-human-approval",audit.get("approval"));assertFalse(audit.get("payload").toString().contains("amount"));
        var applied=snapshot(plan,true);assertTrue(job.apply(plan,approval(preview)).alreadyApplied());assertTrue(job.validate(plan).alreadyApplied());
        assertEquals(applied,snapshot(plan,true));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from audit_event where id=?",plan.operationId()));
        assertThrows(DataAccessException.class,()->jdbc.update("delete from invoice_settlement_line where company_id=?",plan.companyId()));
        assertEquals(applied,snapshot(plan,true));
    }

    @Test void changedApprovalOrFinancialStateNeverAppliesLinks() {
        var plan=fixture();var preview=job().validate(plan);var before=snapshot(plan,true);
        assertThrows(IllegalStateException.class,()->job().apply(plan,new Approval("0".repeat(64),preview.stateSha256(),"synthetic-human-approval")));
        assertEquals(before,snapshot(plan,true));
        jdbc.update("update contract_discount set discount_value=21 where company_id=? and contract_id=?",plan.companyId(),plan.contractId());
        before=snapshot(plan,true);
        assertEquals("APPROVED_STATE_CHANGED",assertThrows(IllegalStateException.class,()->job().apply(plan,approval(preview))).getMessage());
        assertEquals(before,snapshot(plan,true));
    }

    @Test void rejectsForeignInvoiceItemOrTenantWithoutLeakingOrChangingEitherCompany() {
        var own=fixture();var other=fixture();var first=own.entries().getFirst();var foreign=other.entries().getFirst();
        var mixed=new Entry(first.settlementId(),first.receivableId(),first.installmentId(),first.allocationId(),foreign.invoiceId(),foreign.invoiceItemId(),first.expectedAmount(),first.receivedOn(),foreign.invoiceNumber(),foreign.invoiceIssuedOn());
        var plan=copy(own,List.of(mixed,own.entries().getLast()));var before=snapshot(own,true);var otherBefore=snapshot(other,true);
        assertEquals("EXPLICIT_TARGET_NOT_VALID",assertThrows(IllegalStateException.class,()->job().validate(plan)).getMessage());
        var tenant=new HistoricalReceiptLinkPlan(own.operationId(),other.companyId(),own.contractId(),own.serviceId(),own.currency(),own.entries());
        assertEquals("CONTRACT_SCOPE_CHANGED",assertThrows(IllegalStateException.class,()->job().validate(tenant)).getMessage());
        assertEquals(before,snapshot(own,true));assertEquals(otherBefore,snapshot(other,true));
    }

    @Test void rejectsChangedBusinessReferenceAndAnAlreadyPartiallyLinkedHistory() {
        var plan=fixture();var e=plan.entries().getFirst();var before=snapshot(plan,true);
        var wrong=new Entry(e.settlementId(),e.receivableId(),e.installmentId(),e.allocationId(),e.invoiceId(),e.invoiceItemId(),e.expectedAmount(),e.receivedOn(),"OTHER",e.invoiceIssuedOn());
        assertEquals("BUSINESS_REFERENCE_CHANGED",assertThrows(IllegalStateException.class,()->job().validate(copy(plan,List.of(wrong,plan.entries().getLast())))).getMessage());
        assertEquals(before,snapshot(plan,true));
        jdbc.update("insert into invoice_settlement_line(id,company_id,contract_id,receivable_id,settlement_id,invoice_id,invoice_item_id,billing_allocation_id,amount) values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),plan.companyId(),plan.contractId(),e.receivableId(),e.settlementId(),e.invoiceId(),e.invoiceItemId(),e.allocationId(),e.amount());
        before=snapshot(plan,true);
        assertEquals("EXISTING_ADJUSTMENT_OR_LINK",assertThrows(IllegalStateException.class,()->job().validate(plan)).getMessage());
        assertEquals(before,snapshot(plan,true));
    }

    @Test void refusesWithholdingRenegotiationAndChangedAmountsInsteadOfGuessingARepair() {
        var taxPlan=fixture();var e=taxPlan.entries().getFirst();
        taxes.save(TaxObligation.register(taxPlan.companyId(),e.invoiceId(),taxPlan.contractId(),e.receivableId(),"Retenção sintética",null,null,BigDecimal.ONE,TaxTreatment.WITHHELD_BY_CUSTOMER));
        assertEquals("EXISTING_ADJUSTMENT_OR_LINK",assertThrows(IllegalStateException.class,()->job().validate(taxPlan)).getMessage());
        var plan=fixture();e=plan.entries().getFirst();
        jdbc.update("update receivable set status='RENEGOTIATED' where id=? and company_id=?",e.receivableId(),plan.companyId());
        assertEquals("EXPLICIT_TARGET_NOT_VALID",assertThrows(IllegalStateException.class,()->job().validate(plan)).getMessage());
        var amountPlan=fixture();e=amountPlan.entries().getFirst();
        UUID titleId=e.receivableId();
        assertThrows(DataAccessException.class,()->jdbc.update("update receivable set original_amount=original_amount+1 where id=? and company_id=?",titleId,amountPlan.companyId()));
        var wrongAmount=new Entry(e.settlementId(),e.receivableId(),e.installmentId(),e.allocationId(),e.invoiceId(),e.invoiceItemId(),"61.00",e.receivedOn(),e.invoiceNumber(),e.invoiceIssuedOn());
        var wrongPlan=copy(amountPlan,List.of(wrongAmount,amountPlan.entries().getLast()));
        assertEquals("TARGET_AMOUNT_CHANGED",assertThrows(IllegalStateException.class,()->job().validate(wrongPlan)).getMessage());
        assertEquals(0,count("invoice_settlement_line",taxPlan));assertEquals(0,count("invoice_settlement_line",plan));assertEquals(0,count("invoice_settlement_line",amountPlan));
    }

    @Test void auditFailureRollsBackBothAppendsAndLeavesOriginalCashAndHistoryIntact() {
        var plan=fixture();var preview=job().validate(plan);var before=snapshot(plan,true);
        String name="test_receipt_audit_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("create function "+name+"() returns trigger language plpgsql as $$ begin raise exception 'SYNTHETIC_AUDIT_FAILURE'; end $$");
        jdbc.execute("create trigger "+name+" before insert on audit_event for each row when (new.id='"+plan.operationId()+"'::uuid) execute function "+name+"()");
        try {assertThrows(DataAccessException.class,()->job().apply(plan,approval(preview)));}
        finally {jdbc.execute("drop trigger "+name+" on audit_event");jdbc.execute("drop function "+name+"()");}
        assertEquals(before,snapshot(plan,true));assertEquals(0,count("invoice_settlement_line",plan));
    }

    @Test void concurrentApplicationCannotDuplicateLinksAuditOrCash() throws Exception {
        var plan=fixture();var preview=job().validate(plan);var before=snapshot(plan,false);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> action=()->{start.await(10,TimeUnit.SECONDS);try{return job().apply(plan,approval(preview)).status();}catch(RuntimeException conflict){return "REFUSED";}};
            var first=pool.submit(action);var second=pool.submit(action);start.countDown();
            var results=List.of(first.get(30,TimeUnit.SECONDS),second.get(30,TimeUnit.SECONDS));
            assertEquals(1,results.stream().filter("APPLIED"::equals).count());
            assertTrue(results.contains("ALREADY_APPLIED")||results.contains("REFUSED"));
        }
        assertEquals(2,count("invoice_settlement_line",plan));assertEquals(before,snapshot(plan,false));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where company_id=? and id=?",Integer.class,plan.companyId(),plan.operationId()));
    }

    @Test void planAndCommandRequireExplicitTargetsCanonicalMoneyAndSeparateApproval() {
        var plan=fixture();var e=plan.entries().getFirst();
        assertThrows(IllegalStateException.class,()->copy(plan,List.of(e,e)));
        assertThrows(IllegalStateException.class,()->new Entry(e.settlementId(),e.receivableId(),e.installmentId(),e.allocationId(),e.invoiceId(),e.invoiceItemId(),"6e1",e.receivedOn(),e.invoiceNumber(),e.invoiceIssuedOn()));
        assertFalse(Options.parse(new String[]{"--plan","synthetic.json"}).apply());
        assertThrows(IllegalStateException.class,()->Options.parse(new String[]{"--plan","synthetic.json","--apply"}));
        assertEquals(plan.fingerprint(),copy(plan,List.of(plan.entries().getLast(),e)).fingerprint());
    }

    private HistoricalReceiptLinkMaintenance job(){return new HistoricalReceiptLinkMaintenance(jdbc,transactions,clock);}
    private Approval approval(HistoricalReceiptLinkMaintenance.Result preview){return new Approval(preview.planSha256(),preview.stateSha256(),"synthetic-human-approval");}
    private HistoricalReceiptLinkPlan copy(HistoricalReceiptLinkPlan p,List<Entry> entries){return new HistoricalReceiptLinkPlan(p.operationId(),p.companyId(),p.contractId(),p.serviceId(),p.currency(),entries);}
    private long count(String table,HistoricalReceiptLinkPlan p){return jdbc.queryForObject("select count(*) from "+table+" where company_id=? and contract_id=?",Long.class,p.companyId(),p.contractId());}
    private List<Map<String,Object>> snapshot(HistoricalReceiptLinkPlan p,boolean includeNew){
        var result=new ArrayList<Map<String,Object>>();
        var tables=new ArrayList<>(List.of("contract","contract_service","contract_discount","billing_installment","billing_allocation","receivable","receivable_settlement","invoice","invoice_item"));
        if(includeNew)tables.addAll(List.of("invoice_settlement_line","audit_event"));
        for(String table:tables)result.addAll(jdbc.queryForList("select '"+table+"' kind,to_jsonb(t)::text data from "+table+" t where company_id=? order by id",p.companyId()));
        result.addAll(jdbc.queryForList("select 'attribution' kind,to_jsonb(sa)::text data from receivable_settlement_allocation sa where company_id=? order by settlement_id,billing_allocation_id",p.companyId()));
        return result;
    }

    private HistoricalReceiptLinkPlan fixture(){
        var company=companies.save(Company.create("Empresa sintética",null,Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()),UUID.randomUUID()+"@example.test",null,null,null));
        var customer=customers.save(FinalCustomer.create(company.getId(),FinalCustomerType.LEGAL,"Cliente sintético","12345678000195"));
        var work=works.save(Work.create(company.getId(),customer.getId(),"Obra sintética",null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        var contract=contracts.save(Contract.create(company.getId(),work.getId(),"Sintético",ContractPricingMode.FIXED,null,"Contrato sintético",ContractStatus.OPEN,null,null,null,CurrencyCode.BRL));
        var service=services.save(ContractService.create(company.getId(),contract.getId(),"Serviço sintético",null,ContractServiceStatus.COMPLETED,BigDecimal.ONE,new BigDecimal("200.00")));
        discounts.save(ContractDiscount.create(company.getId(),contract.getId(),DiscountType.FIXED,new BigDecimal("20.00")));
        var entries=new ArrayList<Entry>();var date=LocalDate.of(2026,1,12);
        for(String value:List.of("60.00","120.00")){
            var amount=new BigDecimal(value);var charge=installments.save(BillingInstallment.createApproved(company.getId(),contract.getId(),null,null,date,date));
            var allocation=allocations.save(BillingAllocation.createService(company.getId(),charge.getId(),contract.getId(),service.getId(),amount).activate());
            var title=titles.save(Receivable.create(company.getId(),contract.getId(),charge.getId(),UUID.randomUUID(),null,null,date,date,amount));
            var invoice=invoices.save(Invoice.register(company.getId(),contract.getId(),"SYN-"+entries.size(),null,null,date,amount));
            var item=items.save(InvoiceItem.create(company.getId(),invoice.getId(),contract.getId(),allocation.getId(),service.getId(),null,amount));
            var receipt=settlements.save(ReceivableSettlement.confirm(company.getId(),title.getId(),contract.getId(),date,amount,ReceivableSettlementPaymentMethod.PIX,null));
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(status ->
                    origins.attributeSettlement(company.getId(),receipt.getId(),allocation.getId(),amount));
            titles.updateStatus(title.withStatus(com.renovar.canteiro.io.receivables.domain.ReceivableStatus.RECEIVED));
            entries.add(new Entry(receipt.getId(),title.getId(),charge.getId(),allocation.getId(),invoice.getId(),item.getId(),value,date.toString(),invoice.getNumber(),date.toString()));
        }
        return new HistoricalReceiptLinkPlan(UUID.randomUUID(),company.getId(),contract.getId(),service.getId(),CurrencyCode.BRL,entries);
    }
}
