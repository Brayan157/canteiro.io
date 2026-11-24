package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class PreviewInvoiceXmlUseCase {
    public enum PartyMatch { MATCH, MISMATCH, UNDETERMINED }
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final CompanyRepository companies;
    private final WorkRepository works;
    private final FinalCustomerRepository customers;
    private final NfseXmlPreviewParser parser;
    private final ReadInvoiceCoverageUseCase coverage;
    private final AuditEventRecorder audit;
    @Transactional
    public View preview(UUID contractId,UUID installmentId,byte[] bytes){
        authorization.requirePermission(AccessModule.INVOICES,AccessAction.READ);
        var mode=authorization.requireChangeAuthorization(AccessModule.INVOICES,ChangeOperation.CREATE);
        UUID company=tenants.requireCurrentTenant().companyId();
        var contract=contracts.findByIdAndCompanyId(contractId,company).orElseThrow(()->new TenantResourceNotFoundException("Contract"));
        ReadInvoiceCoverageUseCase.View context=null;
        if(installmentId!=null){context=coverage.read(installmentId);if(!contractId.equals(context.contractId()))throw new TenantResourceNotFoundException("Billing installment");}
        var inspection=inspect(company,contract,parser.parse(bytes));
        audit.recordDirectAction(AuditModule.INVOICES,AuditAction.VIEW_SENSITIVE,"InvoiceXmlPreview",null,null,
                Map.of("profile","NFSE_NACIONAL_1_01","hasBlockingIssues",inspection.issues().stream().anyMatch(NfseXmlPreviewParser.Issue::blocking)),Map.of("origin","direct","operation","xml-preview"));
        return new View(contractId,contract.getCurrency().name(),mode,inspection.parsed(),inspection.issuer(),inspection.customer(),context,inspection.issues());
    }

    Inspection inspectFiscal(UUID company, UUID contractId, byte[] bytes) {
        var contract=contracts.findByIdAndCompanyId(contractId,company)
                .orElseThrow(()->new TenantResourceNotFoundException("Contract"));
        return inspect(company,contract,parser.parseFiscal(bytes));
    }

    private Inspection inspect(UUID company, com.renovar.canteiro.io.contracts.domain.Contract contract, NfseXmlPreviewParser.Parsed parsed) {
        String issuer=companies.findById(company).orElseThrow(()->new TenantResourceNotFoundException("Company")).getDocument();
        var work=works.findByIdAndCompanyId(contract.getWorkId(),company).orElseThrow(()->new TenantResourceNotFoundException("Work"));
        String customer=customers.findByIdAndCompanyId(work.getFinalCustomerId(),company).orElseThrow(()->new TenantResourceNotFoundException("Final customer")).getDocument();
        var issuerMatch=match(parsed.issuerDocument(),issuer);var customerMatch=match(parsed.customerDocument(),customer);
        var issues=new ArrayList<>(parsed.issues());
        if(issuerMatch!=PartyMatch.MATCH)issues.add(new NfseXmlPreviewParser.Issue("ISSUER_"+issuerMatch,"partyChecks.issuer","Issuer must be reviewed against the authenticated company",true));
        if(customerMatch!=PartyMatch.MATCH)issues.add(new NfseXmlPreviewParser.Issue("CUSTOMER_"+customerMatch,"partyChecks.customer","Customer must be reviewed against the selected contract chain",true));
        if(contract.getCurrency()!=com.renovar.canteiro.io.shared.domain.CurrencyCode.BRL)
            issues.add(new NfseXmlPreviewParser.Issue("DOCUMENT_CURRENCY_REQUIRES_REVIEW","currency","This national XML preview profile cannot convert its monetary values into another contract currency",true));
        return new Inspection(parsed,issuerMatch,customerMatch,java.util.List.copyOf(issues));
    }
    private PartyMatch match(String reported,String expected){
        if(reported==null||expected==null||expected.isBlank())return PartyMatch.UNDETERMINED;
        return normalize(reported).equals(normalize(expected))?PartyMatch.MATCH:PartyMatch.MISMATCH;
    }
    private String normalize(String value){return value.replaceAll("[^A-Za-z0-9]","").toUpperCase(java.util.Locale.ROOT);}
    public record View(UUID contractId,String currency,ChangeAuthorizationMode mode,NfseXmlPreviewParser.Parsed parsed,PartyMatch issuerMatch,
            PartyMatch customerMatch,ReadInvoiceCoverageUseCase.View billingContext,java.util.List<NfseXmlPreviewParser.Issue> issues) { }
    record Inspection(NfseXmlPreviewParser.Parsed parsed, PartyMatch issuer, PartyMatch customer,
                      java.util.List<NfseXmlPreviewParser.Issue> issues) { }
}
