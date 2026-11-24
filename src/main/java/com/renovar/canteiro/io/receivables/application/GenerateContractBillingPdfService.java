package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.contracts.application.ContractMeasurementAdjustmentAmountProvider;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.ContractNetAmountCalculator;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.domain.ContractBillingReportRepository;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GenerateContractBillingPdfService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final WorkRepository works;
    private final FinalCustomerRepository customers;
    private final CompanyRepository companies;
    private final StorageProvider storage;
    private final ContractDiscountRepository discounts;
    private final ContractMeasurementAdjustmentAmountProvider adjustments;
    private final ContractBillingReportRepository source;
    private final com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository settlements;
    private final ContractBillingPdfGenerator generator;
    private final PlatformTransactionManager transactionManager;
    private final AuditEventRecorder audit;
    private final Clock clock;

    public Generated generate(UUID contractId) {
        var tenant=tenants.requireCurrentTenant();
        requirePermissions();
        Contract contract=read().execute(status->{
            var found=contract(tenant.companyId(),contractId);
            source.validateInvoiceOrigins(tenant.companyId(),contractId);
            return found;
        });
        String label=contract.getContractIdentifier()!=null?contract.getContractIdentifier():contract.getName();
        String name=label.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","-").trim();
        if(name.isBlank()) name="contrato";
        name=name.substring(0,Math.min(100,name.length()));
        String filename="controle-faturamento-"+name+".pdf";
        String correlation=MDC.get("X-Correlation-Id");
        var authentication=SecurityContextHolder.getContext().getAuthentication();
        return new Generated(filename,output->{
            var previous=SecurityContextHolder.getContext();
            var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);tenants.setCurrentTenant(tenant);
            if(correlation!=null) MDC.put("X-Correlation-Id",correlation);
            try {
                ContractBillingPdfGenerator.Result result=read().execute(status->{
                    requirePermissions();
                    var data=data(tenant.companyId(),contractId);
                    try {return generator.write(data,source,output);}
                    catch(IOException error){throw new UncheckedIOException(error);}
                });
                var write=new TransactionTemplate(transactionManager);
                write.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                write.executeWithoutResult(status->audit.recordDirectAction(AuditModule.BILLING,AuditAction.EXPORT,"Contract",contractId,null,null,
                    Map.of("format","BILLING_CONTROL_PDF","serviceRows",result.serviceRows(),"invoiceRows",result.invoiceRows(),"pages",result.pages(),"streamed",true)));
            } catch(UncheckedIOException error){throw error.getCause();}
            finally {tenants.clear();MDC.remove("X-Correlation-Id");SecurityContextHolder.setContext(previous);}
        });
    }
    private void requirePermissions() {
        authorization.requirePermission(AccessModule.BILLING,AccessAction.READ);
        authorization.requirePermission(AccessModule.BILLING,AccessAction.EXPORT);
        authorization.requirePermission(AccessModule.INVOICES,AccessAction.READ);
        authorization.requirePermission(AccessModule.INVOICES,AccessAction.EXPORT);
    }
    private TransactionTemplate read() {
        var transaction=new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }
    private Contract contract(UUID company,UUID id){return contracts.findByIdAndCompanyId(id,company).orElseThrow(()->new TenantResourceNotFoundException("Contract"));}
    private ContractBillingPdfGenerator.Data data(UUID companyId,UUID contractId) {
        var c=contract(companyId,contractId);
        var work=works.findByIdAndCompanyId(c.getWorkId(),companyId).orElseThrow(()->new TenantResourceNotFoundException("Work"));
        var customer=customers.findByIdAndCompanyId(work.getFinalCustomerId(),companyId).orElseThrow(()->new TenantResourceNotFoundException("Customer"));
        var company=companies.findById(companyId).orElseThrow();
        source.validateInvoiceOrigins(companyId,contractId);
        var amounts=ContractNetAmountCalculator.calculateSubtotal(source.serviceSubtotal(companyId,contractId),
            discounts.findActiveByContractIdAndCompanyId(contractId,companyId).orElse(null),adjustments.approvedAmount(companyId,contractId));
        byte[] logo=null;
        String key=company.getLogo();
        if(key!=null && key.matches("companies/"+companyId+"/logo-[A-Za-z0-9-]+\\.(png|jpg|webp)")) {
            try {byte[] bytes=storage.load(key);if(bytes.length<=2*1024*1024) logo=bytes;}catch(RuntimeException ignored){/* private-logo fallback */}
        }
        String contractNumber = c.getReference() != null ? c.getReference() : c.getContractIdentifier();
        return new ContractBillingPdfGenerator.Data(companyId,contractId,company.getTradeName()==null||company.getTradeName().isBlank()?company.getCorporateName():company.getTradeName(),
            company.getDocument(),logo,customer.getName(),work.getName(),(c.getContractIdentifier()==null?"Identificação não informada":c.getContractIdentifier())+" - "+c.getName(),
            c.getStatus().name(),c.getCurrency(),clock.instant(),amounts,source.invoicedAmount(companyId,contractId),source.unassignedInvoicedAmount(companyId,contractId),
            authorization.hasPermission(AccessModule.RECEIVABLES,AccessAction.READ)
                && authorization.hasPermission(AccessModule.RECEIVABLES,AccessAction.EXPORT)
                ? settlements.confirmedAmountsByContractIdsAndCompanyId(java.util.List.of(contractId),companyId)
                    .getOrDefault(contractId,java.math.BigDecimal.ZERO.setScale(2)) : null,contractNumber);
    }
    public record Generated(String filename,Writer writer) { }
    @FunctionalInterface public interface Writer {void write(OutputStream output) throws IOException;}
}
