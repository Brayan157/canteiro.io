package com.renovar.canteiro.io.receivables;

import com.renovar.canteiro.io.AbstractPostgresIntegrationTest;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.PermissionRepository;
import com.renovar.canteiro.io.access.domain.Role;
import com.renovar.canteiro.io.access.domain.RolePermission;
import com.renovar.canteiro.io.access.domain.RolePermissionRepository;
import com.renovar.canteiro.io.access.domain.RoleRepository;
import com.renovar.canteiro.io.access.domain.UserRole;
import com.renovar.canteiro.io.access.domain.UserRoleRepository;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractDiscount;
import com.renovar.canteiro.io.contracts.domain.ContractDiscountRepository;
import com.renovar.canteiro.io.contracts.domain.DiscountType;
import com.renovar.canteiro.io.contracts.domain.ContractPricingMode;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractServiceStatus;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.customers.domain.FinalCustomer;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
import com.renovar.canteiro.io.customers.domain.FinalCustomerType;
import com.renovar.canteiro.io.identity.application.PasswordHasher;
import com.renovar.canteiro.io.identity.domain.CompanyUser;
import com.renovar.canteiro.io.identity.domain.CompanyUserRepository;
import com.renovar.canteiro.io.identity.domain.User;
import com.renovar.canteiro.io.identity.domain.UserRepository;
import com.renovar.canteiro.io.identity.domain.UserType;
import com.renovar.canteiro.io.platform.company.domain.Company;
import com.renovar.canteiro.io.platform.company.domain.CompanyRepository;
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachment;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceAttachmentType;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.shared.infrastructure.storage.StorageProvider;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InvoiceXmlAndCoverageApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BASE = "/api/v1/company/invoices";
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private ContractDiscountRepository discounts;
    @Autowired private ContractServiceRepository services;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceAttachmentRepository attachments;
    @Autowired private StorageProvider storage;
    @Autowired private InvoiceItemRepository items;
    @Autowired private BillingInstallmentRepository installments;
    @Autowired private BillingAllocationRepository allocations;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private PasswordHasher hasher;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void workBillingTotalsCoverAllPagesAndPdfListsContractsBeforeCurrencyTotals() throws Exception {
        var base = fixture();
        grantWorkBilling(base, true);
        var contract = contracts.findByIdAndCompanyId(base.contractId(), base.companyId()).orElseThrow();
        var baseWork = works.findByIdAndCompanyId(contract.getWorkId(), base.companyId()).orElseThrow();
        UUID workId = works.save(Work.create(base.companyId(), baseWork.getFinalCustomerId(), "Usina de teste", "OBRA-42",
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null)).getId();
        var owner = additionalContract(base, workId, "Primeiro contrato", "CT-001/26", CurrencyCode.BRL);
        var secondBrl = additionalContract(owner, workId, "Segundo contrato", "CT-002/26", CurrencyCode.BRL);
        var eur = additionalContract(owner, workId, "Contrato em euro", "CT-EUR/26", CurrencyCode.EUR);
        var usd = additionalContract(owner, workId, "Contrato em dólar", null, CurrencyCode.USD);
        String path = "/api/v1/company/billing-control/works/" + workId;
        var first = allocation(owner, charge(owner), "Serviço homônimo", "100.00");
        var second = allocation(owner, charge(owner), "Complemento", "200.00");
        allocation(secondBrl, charge(secondBrl), "Serviço homônimo", "100.00");
        var europeanLine = allocation(eur, charge(eur), "Serviço homônimo", "50.00");
        var legacyUsdLine = allocation(usd, charge(usd), "Origem legada", "1100.00");
        register(owner, "NF-ATIVA", List.of(line(first, "100.00"), line(second, "200.00")), 201);
        register(eur, "NF-EUR", List.of(line(europeanLine, "50.00")), 201);
        var legacyUsd = invoices.save(Invoice.register(usd.companyId(), usd.contractId(), "NF-USD-LEGADA", null, null,
                LocalDate.of(2026, 11, 1), new BigDecimal("1100.00")));
        items.save(InvoiceItem.create(usd.companyId(), legacyUsd.getId(), usd.contractId(), legacyUsdLine.getId(),
                legacyUsdLine.getContractServiceId(), null, new BigDecimal("1100.00")));
        invoices.save(Invoice.register(owner.companyId(), owner.contractId(), "NF-CANCELADA", null, null,
                LocalDate.of(2026, 11, 2), new BigDecimal("100.00")).cancel());
        discounts.save(ContractDiscount.create(owner.companyId(), owner.contractId(), DiscountType.FIXED, new BigDecimal("100.00")));

        var result = call(owner, get(path).param("size", "1"), null, 200);
        var pageTwo = call(owner, get(path).param("page", "1").param("size", "1"), null, 200);
        assertEquals(4, result.at("/contracts/totalItems").asInt());
        assertEquals(1, result.at("/contracts/items").size());
        assertFalse(result.at("/contracts/items/0/contractId").equals(pageTwo.at("/contracts/items/0/contractId")));
        assertEquals(result.path("totalsByCurrency"), pageTwo.path("totalsByCurrency"));
        var brl = result.at("/totalsByCurrency/0");
        var dollar = result.at("/totalsByCurrency/1");
        var euro = result.at("/totalsByCurrency/2");
        assertEquals("BRL", brl.path("currency").asText());
        assertEquals(2, brl.path("contractCount").asInt());
        assertEquals("2900.00", brl.path("contractNetAmount").asText());
        assertEquals("300.00", brl.path("invoicedAmount").asText());
        assertEquals("2600.00", brl.path("documentaryBalance").asText());
        assertEquals("USD", dollar.path("currency").asText());
        assertEquals("-100.00", dollar.path("documentaryBalance").asText());
        assertEquals("EUR", euro.path("currency").asText());
        assertEquals("950.00", euro.path("documentaryBalance").asText());
        var rows = new java.util.HashMap<String, JsonNode>();
        for (var row : call(owner, get(path), null, 200).at("/contracts/items")) {
            rows.put(row.path("contractId").asText(), row);
        }
        var firstContract = rows.get(owner.contractId().toString());
        assertEquals("IDENTIFICADOR-INTERNO", firstContract.path("contractIdentifier").asText());
        assertEquals("CT-001/26", firstContract.path("contractReference").asText());
        assertTrue(rows.get(usd.contractId().toString()).path("contractReference").isNull());
        assertEquals("1900.00", call(owner, get("/api/v1/company/billing-control/contracts/" + owner.contractId()),
                null, 200).path("contractNetAmount").asText());

        String pdf = billingPdf(owner, path + "/report/pdf");
        assertTrue(pdf.contains("Usina de teste") && pdf.contains("Referência da obra: OBRA-42"));
        assertTrue(pdf.contains("Nome do contrato") && pdf.contains("Número do contrato") && pdf.contains("Valor do contrato"));
        assertTrue(pdf.contains("Valor faturado") && pdf.contains("Saldo"));
        assertTrue(pdf.contains("CT-001/26") && pdf.contains("CT-002/26") && pdf.contains("CT-EUR/26"));
        assertTrue(pdf.contains("Não informado"));
        assertFalse(pdf.contains("IDENTIFICADOR-INTERNO"));
        assertTrue(pdf.indexOf("Usina de teste") < pdf.indexOf("Referência da obra: OBRA-42"));
        assertTrue(pdf.indexOf("Referência da obra: OBRA-42") < pdf.indexOf("Nome do contrato"));
        assertTrue(pdf.indexOf("Nome do contrato") < pdf.indexOf("Primeiro contrato"));
        assertTrue(pdf.indexOf("Primeiro contrato") < pdf.indexOf("RESUMO TOTAL POR MOEDA"));
        assertTrue(pdf.contains("RESUMO TOTAL POR MOEDA"));
        String[] sections = pdf.split("RESUMO TOTAL POR MOEDA", 2);
        for (String amount : List.of("R$ 1.900,00", "R$ 300,00", "R$ 1.600,00",
                "US$ 1.000,00", "US$ 1.100,00", "-US$ 100,00", "€ 1.000,00", "€ 50,00", "€ 950,00")) {
            assertTrue(sections[0].contains(amount), amount);
        }
        for (String amount : List.of("R$ 2.900,00", "R$ 300,00", "R$ 2.600,00",
                "US$ 1.000,00", "US$ 1.100,00", "-US$ 100,00", "€ 1.000,00", "€ 50,00", "€ 950,00")) {
            assertTrue(sections[1].contains(amount), amount);
        }
        assertTrue(pdf.contains("Segundo contrato") && pdf.contains("Contrato em euro"));
        assertFalse(pdf.contains(owner.contractId().toString()) || pdf.contains(workId.toString()));
        assertFalse(pdf.contains("NF-CANCELADA") || pdf.contains("NF-ATIVA") || pdf.contains("Serviço homônimo"));
        assertFalse(pdf.contains("COBRANÇAS EM ABERTO"));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_event WHERE company_id = ? AND entity_id = ? AND action = 'EXPORT'
                """, Integer.class, owner.companyId(), workId));
    }

    @Test
    void workBillingRequiresTenantAndPermissionsAndHandlesEmptyOrUnreconciledWork() throws Exception {
        var owner = fixture();
        grantWorkBilling(owner, true);
        var baseWork = works.findByIdAndCompanyId(contracts.findByIdAndCompanyId(owner.contractId(),
                owner.companyId()).orElseThrow().getWorkId(), owner.companyId()).orElseThrow();
        var empty = works.save(Work.create(owner.companyId(), baseWork.getFinalCustomerId(), "Obra vazia", null,
                WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        String path = "/api/v1/company/billing-control/works/" + empty.getId();
        var response = call(owner, get(path), null, 200);
        assertTrue(response.path("totalsByCurrency").isEmpty());
        assertEquals(0, response.at("/contracts/totalItems").asInt());
        assertTrue(billingPdf(owner, path + "/report/pdf").contains("Nenhum contrato nesta obra"));
        call(owner, get(path).param("size", "101"), null, 400);

        var limited = actor(owner.companyId(), owner.contractId(), AccessAction.READ);
        grant(limited, AccessModule.BILLING, AccessAction.READ);
        grantWorkBilling(limited, false);
        call(limited, get(path), null, 200);
        call(limited, get(path + "/report/pdf"), null, 403);
        var withoutInvoices = actor(owner.companyId(), owner.contractId());
        grant(withoutInvoices, AccessModule.BILLING, AccessAction.READ);
        grantWorkBilling(withoutInvoices, false);
        call(withoutInvoices, get(path), null, 403);
        var outsider = fixture();
        grantWorkBilling(outsider, true);
        call(outsider, get(path), null, 404);
        call(outsider, get(path + "/report/pdf"), null, 404);

        invoices.save(Invoice.register(owner.companyId(), owner.contractId(), "NF-SEM-ORIGEM", null, null,
                LocalDate.of(2026, 11, 1), new BigDecimal("5.00")));
        String invalid = "/api/v1/company/billing-control/works/" + baseWork.getId();
        assertEquals("BILLING_REPORT_RECONCILIATION_REQUIRED", call(owner, get(invalid), null, 409).path("reason").asText());
        call(owner, get(invalid + "/report/pdf"), null, 409);
    }

    private Fixture additionalContract(Fixture owner, UUID workId, String name, String reference, CurrencyCode currency) {
        var contract = contracts.save(Contract.create(owner.companyId(), workId, "IDENTIFICADOR-INTERNO", ContractPricingMode.FIXED,
                reference, name, ContractStatus.ACTIVE, null, null, null, currency));
        return new Fixture(owner.companyId(), owner.userId(), owner.roleId(), contract.getId());
    }

    private void grantWorkBilling(Fixture actor, boolean export) {
        grant(actor, AccessModule.WORKS, AccessAction.READ);
        if (export) {
            grant(actor, AccessModule.BILLING, AccessAction.EXPORT);
            grant(actor, AccessModule.INVOICES, AccessAction.EXPORT);
        }
    }

    @Test
    void contractBillingPdfUsesCommercialReferenceInNumberColumnAndKeepsLegacyFallback() throws Exception {
        var base = fixture();
        grantWorkBilling(base, true);
        var contract = contracts.findByIdAndCompanyId(base.contractId(), base.companyId()).orElseThrow();
        var numbered = additionalContract(base, contract.getWorkId(), "Contrato com número", "CT-PDF/26", CurrencyCode.BRL);
        var legacy = additionalContract(base, contract.getWorkId(), "Contrato legado", null, CurrencyCode.BRL);
        allocation(numbered, charge(numbered), "Serviço com número", "100.00");
        allocation(legacy, charge(legacy), "Serviço legado", "100.00");
        String prefix = "/api/v1/company/billing-control/contracts/";
        String pdf = billingPdf(numbered, prefix + numbered.contractId() + "/report/pdf");
        int column = pdf.indexOf("Número do contrato");
        assertTrue(column >= 0);
        assertTrue(pdf.indexOf("IDENTIFICADOR-INTERNO") >= 0 && pdf.indexOf("IDENTIFICADOR-INTERNO") < column);
        assertTrue(pdf.indexOf("CT-PDF/26", column) >= 0);
        String legacyPdf = billingPdf(legacy, prefix + legacy.contractId() + "/report/pdf");
        int legacyColumn = legacyPdf.indexOf("Número do contrato");
        assertTrue(legacyColumn >= 0 && legacyPdf.indexOf("IDENTIFICADOR-INTERNO", legacyColumn) >= 0);
    }

    private String billingPdf(Fixture actor, String path) throws Exception {
        var pending = mvc.perform(get(path).with(jwt().jwt(token -> token.subject(actor.userId().toString()))))
                .andExpect(request().asyncStarted()).andReturn();
        var response = mvc.perform(asyncDispatch(pending)).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse();
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getHeader("Content-Disposition").startsWith("inline;"));
        try (var pdf = org.apache.pdfbox.Loader.loadPDF(response.getContentAsByteArray())) {
            return new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
        }
    }

    @Test
    void preservesOnlyMatchedFiscalEvidenceWithoutChangingOfficialAmounts() throws Exception {
        var owner = fixture();
        var allocation = allocation(owner, charge(owner), "Evidência", "1000.00");
        var invoice = invoices.save(Invoice.register(owner.companyId(), owner.contractId(), "17", "NF1", null,
                LocalDate.of(2026, 11, 2), new BigDecimal("1000.00")));
        items.save(InvoiceItem.create(owner.companyId(), invoice.getId(), owner.contractId(), allocation.getId(),
                allocation.getContractServiceId(), null, new BigDecimal("1000.00")));
        String path = BASE + "/" + invoice.getId() + "/xml-fiscal-snapshot";
        String key = "1".repeat(50);
        String raw = new String(synthetic(owner), java.nio.charset.StandardCharsets.UTF_8)
                .replace("<infNFSe>", "<infNFSe Id='NFS" + key + "'><dhProc>2026-11-03T23:55:00-03:00</dhProc>")
                .replace("DPS9", "900")
                .replace("<vServPrest>", "<vDedRed><vDR>12.00</vDR></vDedRed><vServPrest>");
        var wrong = storeLegacyXml(owner, invoice, raw.replace("<nNFSe>17</nNFSe>", "<nNFSe>18</nNFSe>"));
        var matching = storeLegacyXml(owner, invoice, raw);
        var invalidTimestamp = storeLegacyXml(owner, invoice, raw.replace("2026-11-03T23:55:00-03:00", "2026-11-03"));
        var requester = actor(owner.companyId(), owner.contractId(), AccessAction.READ, AccessAction.REQUEST_CREATE);
        var noRead = actor(owner.companyId(), owner.contractId(), AccessAction.CREATE_DIRECT);
        var outsider = fixture();
        var before = snapshot(owner);
        var body = Map.of("attachmentId", matching);
        call(owner, get(path), null, 404);
        call(requester, put(path), body, 403);
        call(noRead, get(path), null, 403);
        call(outsider, put(path), body, 404);
        call(owner, put(path), Map.of("attachmentId", wrong), 409);
        call(owner, put(path), Map.of("attachmentId", invalidTimestamp), 400);
        call(owner, get(path), null, 404);

        var created = call(owner, put(path), body, 201);
        assertEquals("2026-11-02", created.path("registeredIssuedOn").asText());
        assertEquals("2026-11-03T23:55:00-03:00", created.at("/facts/processedAt/value").asText());
        assertEquals("2026-11-01", created.at("/facts/dps/competenceOn/value").asText());
        assertEquals("900.00", created.at("/facts/totals/netAmount/value").asText());
        assertEquals("12.00", created.at("/facts/reportedFields/declaredDeductionAmount/value").asText());
        assertEquals("100.00", created.at("/facts/taxes/0/amount/value").asText());
        assertFalse(created.toString().contains(key));
        assertFalse(created.toString().contains("12345678000195"));
        assertFalse(created.toString().contains("storageKey"));
        assertEquals(created, call(owner, put(path), body, 200));
        assertEquals(created, call(requester, get(path), null, 200));
        call(owner, put(path), Map.of("attachmentId", wrong), 409);
        assertEquals(before, snapshot(owner));
        assertEquals(0, count("change_request", owner));
        assertEquals(0, count("tax_obligation", owner));
        assertEquals(1, jdbc.queryForObject("""
                SELECT count(*) FROM audit_event WHERE company_id = ? AND entity_type = 'InvoiceXmlFiscalSnapshot'
                """, Integer.class, owner.companyId()));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM invoice_xml_fiscal_snapshot WHERE invoice_id = ?", invoice.getId()));

        var docs = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        var operations = docs.path("paths").path(BASE + "/{invoiceId}/xml-fiscal-snapshot");
        assertTrue(operations.path("get").path("responses").has("200"));
        assertTrue(operations.path("put").path("responses").has("201"));
        assertTrue(operations.path("put").path("responses").has("409"));
    }

    private UUID storeLegacyXml(Fixture actor, Invoice invoice, String text) throws Exception {
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String key = "invoices/" + actor.companyId() + "/" + invoice.getId() + "/" + UUID.randomUUID() + "-synthetic.xml";
        storage.store(key, bytes);
        String sha256 = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        return attachments.save(InvoiceAttachment.create(actor.companyId(), invoice.getId(), invoice.getContractId(),
                InvoiceAttachmentType.XML, "synthetic.xml", "application/xml", bytes.length, sha256, key, actor.userId())).getId();
    }

    @Test void fullServicesCanBeDocumentedSeparatelyButNeverFragmented()throws Exception{
        var f=fixture();var charge=charge(f);var a=allocation(f,charge,"A","100.00");var b=allocation(f,charge,"B","100.00");
        var initial=read(f,charge);assertEquals(2,initial.path("services").size());
        assertEquals("INVOICE_SERVICE_GROUP_INCOMPLETE",register(f,"PART",List.of(line(a,"50.00")),409).path("reason").asText());
        register(f,"A",List.of(line(a,"100.00")),201);
        var covered=read(f,charge);assertEquals("200.00",covered.path("amount").asText());assertEquals("100.00",covered.path("documentedAmount").asText());assertEquals("100.00",covered.path("remainingToDocumentAmount").asText());assertEquals("FULLY_DOCUMENTED",group(covered,a).path("state").asText());
        assertEquals("NOT_DOCUMENTED",group(covered,b).path("state").asText());assertEquals("100.00",group(covered,b).path("availableToDocumentAmount").asText());
        register(f,"B",List.of(line(b,"100.00")),201);
        assertEquals("INVOICE_SERVICE_GROUP_ALREADY_DOCUMENTED",register(f,"AGAIN",List.of(line(a,"100.00")),409).path("reason").asText());
        assertEquals(2,count("invoice",f));
    }
    @Test void allOriginsOfTheSameChargedServiceMustBeIncludedTogether()throws Exception{
        var f=fixture();var charge=charge(f);var first=allocation(f,charge,"Mesmo serviço","40.00");
        var second=allocations.save(BillingAllocation.createService(f.companyId(),charge.getId(),f.contractId(),first.getContractServiceId(),new BigDecimal("60.00")).activate());
        var before=read(f,charge);assertEquals(1,before.path("services").size());assertEquals(2,before.at("/services/0/origins").size());String id=before.at("/services/0/billingServiceGroupId").asText();
        register(f,"OMITTED",List.of(line(first,"40.00")),409);
        register(f,"ALL",List.of(line(first,"40.00"),line(second,"60.00")),201);
        assertEquals(id,read(f,charge).at("/services/0/billingServiceGroupId").asText());
    }
    @Test void oneInvoiceAcrossChargesCountsOnlyEachChargesExactLines()throws Exception{
        var f=fixture();var first=charge(f);var second=charge(f);var a=allocation(f,first,"A","100.00");
        var b=allocations.save(BillingAllocation.createService(f.companyId(),second.getId(),f.contractId(),a.getContractServiceId(),new BigDecimal("100.00")).activate());
        register(f,"BOTH",List.of(line(a,"100.00"),line(b,"100.00")),201);
        assertEquals("100.00",read(f,first).at("/services/0/documentedAmount").asText());assertEquals("100.00",read(f,second).at("/services/0/documentedAmount").asText());
    }
    @Test void legacyPartialRemainsReadableUnchangedAndRequiresReview()throws Exception{
        var f=fixture();var charge=charge(f);var a=allocation(f,charge,"Legado","100.00");
        var nf=invoices.save(Invoice.register(f.companyId(),f.contractId(),"LEGACY",null,null,LocalDate.of(2026,11,1),new BigDecimal("40.00")));
        items.save(InvoiceItem.create(f.companyId(),nf.getId(),f.contractId(),a.getId(),a.getContractServiceId(),null,new BigDecimal("40.00")));
        var before=snapshot(f);var coverage=read(f,charge);assertEquals("LEGACY_PARTIAL",coverage.at("/services/0/state").asText());assertFalse(coverage.at("/services/0/selectable").asBoolean());
        register(f,"COMPLETE",List.of(line(a,"60.00")),409);assertEquals(before,snapshot(f));
    }
    @Test void pendingCreatesNoCoverageAndApprovalRevalidatesTheCurrentGroup()throws Exception{
        var f=fixture();var requester=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.REQUEST_CREATE);
        var approver=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.APPROVE);
        var charge=charge(f);var a=allocation(f,charge,"A","100.00");
        register(requester,"PART",List.of(line(a,"50.00")),409);
        var optional = new java.util.LinkedHashMap<>(payload(requester, "PENDING", List.of(line(a, "100.00"))));
        optional.put("justification", "");
        assertEquals("Justification must have between 1 and 1000 characters when provided",
                call(requester, post(BASE), optional, 400).path("detail").asText());
        assertEquals(0, count("change_request", f));
        var pending=register(requester,"PENDING",List.of(line(a,"100.00")),202);String id=pending.path("changeRequestId").asText();
        assertFalse(id.isBlank());assertEquals(0,count("invoice",f));assertEquals("NOT_DOCUMENTED",read(f,charge).at("/services/0/state").asText());
        call(requester,post("/api/v1/company/change-requests/"+id+"/approve"),Map.of(),403);
        register(f,"OFFICIAL",List.of(line(a,"100.00")),201);
        call(approver,post("/api/v1/company/change-requests/"+id+"/approve"),Map.of(),409);assertEquals(1,count("invoice",f));
        var next=charge(f);var b=allocation(f,next,"B","100.00");
        var explicitNull = new java.util.LinkedHashMap<>(payload(requester, "APPROVED", List.of(line(b, "100.00"))));
        explicitNull.put("justification", null);
        var valid = call(requester, post(BASE), explicitNull, 202);
        call(approver,post("/api/v1/company/change-requests/"+valid.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals("FULLY_DOCUMENTED",read(f,next).at("/services/0/state").asText());
    }
    @Test void concurrentFullGroupRegistrationAllowsOneAndFiscalDuplicateReturnsSafeConflict()throws Exception{
        var f=fixture();var charge=charge(f);var a=allocation(f,charge,"A","100.00");var latch=new java.util.concurrent.CountDownLatch(1);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var one=executor.submit(()->{latch.await();return code(f,"C1",a);});var two=executor.submit(()->{latch.await();return code(f,"C2",a);});latch.countDown();
            var results=List.of(one.get(45,java.util.concurrent.TimeUnit.SECONDS),two.get(45,java.util.concurrent.TimeUnit.SECONDS));assertTrue(results.contains(201));assertTrue(results.contains(409));
        }
        var another=charge(f);var b=allocation(f,another,"B","100.00");
        String previous=jdbc.queryForObject("select number from invoice where company_id=?",String.class,f.companyId());
        assertEquals("INVOICE_FISCAL_IDENTITY_ALREADY_REGISTERED",register(f,previous,List.of(line(b,"100.00")),409).path("reason").asText());assertEquals(1,count("invoice",f));
    }
    @Test void previewProvidesTaxProvenanceAndNoFinancialEffectsEvenForApprovalAuthority()throws Exception{
        var f=fixture();var requester=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.REQUEST_CREATE);
        byte[] xml=new String(synthetic(f),java.nio.charset.StandardCharsets.UTF_8)
                .replace("<infNFSe>","<infNFSe><dhProc>2026-11-02T23:55:00-03:00</dhProc>")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var before=snapshot(f);var response=preview(requester,xml,200);
        assertEquals("REQUEST_APPROVAL",response.path("creationMode").asText());assertEquals("17",response.at("/prefill/number/value").asText());assertEquals("991",response.at("/dps/number/value").asText());
        assertEquals("2026-11-02",response.at("/prefill/issuedOn/value").asText());
        assertEquals("/NFSe/infNFSe/dhProc",response.at("/prefill/issuedOn/sourcePath").asText());
        assertEquals("NOT_PERFORMED",response.at("/source/schemaValidationStatus").asText());assertEquals("RETAINED",response.at("/taxCandidates/0/withholdingStatus").asText());
        assertEquals("1000.00",response.at("/sourceTotals/commercialAmountCandidate/value").asText());assertTrue(response.at("/sourceTotals/netAmount/value").isString());
        assertEquals("MATCH",response.at("/partyChecks/issuer").asText());assertFalse(response.toString().contains(companies.findById(f.companyId()).orElseThrow().getDocument()));
        assertEquals(before,snapshot(f));assertEquals(0,count("change_request",f));assertEquals(0,count("tax_obligation",f));assertEquals(0,count("invoice_attachment",f));
        String audit=jdbc.queryForObject("select string_agg(coalesce(after_data::text,'')||coalesce(metadata::text,''),'') from audit_event where company_id=?",String.class,f.companyId());
        assertEquals("Prévia de XML de nota fiscal",jdbc.queryForObject("select entity_display_name from audit_event where company_id=? and entity_type='InvoiceXmlPreview'",String.class,f.companyId()));
        assertTrue(jdbc.queryForObject("select event_summary from audit_event where company_id=? and entity_type='InvoiceXmlPreview'",String.class,f.companyId()).contains("consultou"));
        assertFalse(audit.contains("1000.00"));assertFalse(audit.contains("12345678000195"));assertFalse(audit.contains("nDPS"));
    }
    @Test void tenantPermissionsAndMalformedUploadsCannotInferAnotherCompany()throws Exception{
        var f=fixture();var other=fixture();var charge=charge(f);allocation(f,charge,"A","100.00");
        call(other,get(coveragePath(charge)),null,404);
        var readOnly=actor(f.companyId(),f.contractId(),AccessAction.READ);call(readOnly,get(coveragePath(charge)),null,403);preview(readOnly,synthetic(f),403);
        mvc.perform(get(coveragePath(charge))).andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE+"/xml/preview").file(new org.springframework.mock.web.MockMultipartFile("file","model.xml","application/xml",synthetic(f)))
                .param("contractId",f.contractId().toString()).with(jwt().jwt(t->t.subject(other.userId().toString())))).andExpect(status().isNotFound());
        byte[] malicious=("<!DOCTYPE NFSe [<!ENTITY external SYSTEM 'file:///never-read'>]>"+new String(synthetic(f),java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        preview(f,malicious,400);assertEquals(0,count("invoice",f));
    }
    @Test void changedMembershipAndCancellationBetweenReadAndWriteCannotFreezeAStaleSelection()throws Exception{
        var f=fixture();var charge=charge(f);var a=allocation(f,charge,"A","100.00");String groupId=read(f,charge).at("/services/0/billingServiceGroupId").asText();
        var requester=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.REQUEST_CREATE);
        var approver=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.APPROVE);
        var pending=register(requester,"STALE",List.of(line(a,"100.00")),202);
        var added=allocations.save(BillingAllocation.createService(f.companyId(),charge.getId(),f.contractId(),a.getContractServiceId(),new BigDecimal("50.00")).activate());
        assertEquals(groupId,read(f,charge).at("/services/0/billingServiceGroupId").asText());
        register(f,"OLD",List.of(line(a,"100.00")),409);
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertEquals(0,count("invoice",f));
        register(f,"CURRENT",List.of(line(a,"100.00"),line(added,"50.00")),201);
        var other=charge(f);var b=allocation(f,other,"B","100.00");read(f,other);
        allocations.updateStatus(b.cancel());register(f,"CANCELLED-ORIGIN",List.of(line(b,"100.00")),409);
        assertEquals(1,count("invoice",f));
    }
    @Test void confirmedDocumentAmountMustMatchWholeOriginsAlsoWhenApprovalApplies()throws Exception{
        var f=fixture();var charge=charge(f);var a=allocation(f,charge,"A","100.00");
        var mismatch=new java.util.LinkedHashMap<String,Object>(payload(f,"MISMATCH",List.of(line(a,"100.00"))));mismatch.put("documentTotalAmount","50.00");
        assertEquals("INVOICE_DOCUMENT_TOTAL_MISMATCH",call(f,post(BASE),mismatch,409).path("reason").asText());
        mismatch.put("documentTotalAmount",100);call(f,post(BASE),mismatch,400);
        mismatch.put("documentTotalAmount","100.001");call(f,post(BASE),mismatch,400);
        mismatch.put("documentTotalAmount","100.00");
        var requester=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.REQUEST_CREATE);
        var approver=actor(f.companyId(),f.contractId(),AccessAction.READ,AccessAction.APPROVE);
        var proposal=call(requester,post(BASE),mismatch,202);String id=proposal.path("changeRequestId").asText();
        assertEquals(0,count("invoice",f));
        assertTrue(jdbc.queryForObject("select proposed_data::text from change_request where id=?",String.class,UUID.fromString(id)).contains("documentTotalAmount"));
        call(approver,post("/api/v1/company/change-requests/"+id+"/approve"),Map.of(),200);
        assertEquals(1,count("invoice",f));
    }
    @Test void multipartMissingFileAndOversizedFileReturnControlledProblems()throws Exception{
        var f=fixture();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE+"/xml/preview").param("contractId",f.contractId().toString())
                .with(jwt().jwt(t->t.subject(f.userId().toString())))).andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE+"/xml/preview").file(new org.springframework.mock.web.MockMultipartFile("file","model.xml","application/xml",new byte[10*1024*1024+1]))
                .param("contractId",f.contractId().toString()).with(jwt().jwt(t->t.subject(f.userId().toString())))).andExpect(status().is(413)).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
    @Test void executableContractDeclaresNewRoutesWithoutSensitivePartyDocuments()throws Exception{
        String text=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();var api=json.readTree(text);
        assertTrue(api.path("paths").has("/api/v1/company/invoices/xml/preview"));
        var upload=api.path("paths").path("/api/v1/company/invoices/xml/preview").path("post").path("requestBody").path("content").path("multipart/form-data").path("schema");
        assertEquals("object",upload.path("type").asText());assertEquals("binary",upload.at("/properties/file/format").asText());assertTrue(api.path("paths").has("/api/v1/company/billing-installments/{billingInstallmentId}/invoice-coverage"));
        assertTrue(api.at("/components/schemas/InvoiceCoverageOriginResponse/properties/amount/type").toString().contains("string"));
        assertFalse(api.at("/components/schemas/InvoiceXmlPreviewResponse/properties").has("issuerDocument"));
        String path=System.getenv("INVOICE_XML_OPENAPI_EXPORT_PATH");if(path!=null&&!path.isBlank())Files.writeString(Path.of(path),text);
    }
    private Fixture fixture(){
        String suffix=UUID.randomUUID().toString();String document=new java.math.BigInteger(suffix.replace("-",""),16).mod(new java.math.BigInteger("100000000000000")).toString();document="0".repeat(14-document.length())+document;
        var company=companies.save(Company.create("Emitente sintético",null,document,suffix+"@example.test",null,null,null));
        var customer=customers.save(FinalCustomer.create(company.getId(),FinalCustomerType.LEGAL,"Cliente sintético","12345678000195"));
        var work=works.save(Work.create(company.getId(),customer.getId(),"Obra",null,WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION,null,WorkStatus.ACTIVE,null,null,null));
        var contract=contracts.save(Contract.create(company.getId(),work.getId(),"REF",ContractPricingMode.FIXED,"REF","Contrato",ContractStatus.ACTIVE,null,null,null,CurrencyCode.BRL));
        var f=actor(company.getId(),contract.getId(),AccessAction.READ,AccessAction.CREATE_DIRECT);grant(f,AccessModule.BILLING,AccessAction.READ);return f;
    }
    private Fixture actor(UUID company,UUID contract,AccessAction... actions){
        User user=User.create(UUID.randomUUID()+"@example.test",UserType.COMPANY);user.activate(hasher.hash("Canteiro#2026Seguro"),Instant.now());user=users.save(user);memberships.save(CompanyUser.create(user.getId(),company));
        var role=roles.save(Role.create(company,"Perfil "+UUID.randomUUID(),null));userRoles.save(UserRole.create(user.getId(),role.getId(),company));var f=new Fixture(company,user.getId(),role.getId(),contract);
        for(var action:actions)grant(f,AccessModule.INVOICES,action);return f;
    }
    private void grant(Fixture f,AccessModule module,AccessAction action){rolePermissions.save(RolePermission.create(f.roleId(),permissions.findByModuleAndAction(module,action).orElseThrow().getId()));}
    private BillingInstallment charge(Fixture f){return installments.save(BillingInstallment.createApproved(f.companyId(),f.contractId(),"Cobrança",null,LocalDate.of(2026,11,1),LocalDate.of(2026,12,1)));}
    private BillingAllocation allocation(Fixture f,BillingInstallment charge,String title,String amount){var service=services.save(ContractService.create(f.companyId(),f.contractId(),title,null,ContractServiceStatus.ACTIVE,BigDecimal.ONE,new BigDecimal("1000.00")));
        return allocations.save(BillingAllocation.createService(f.companyId(),charge.getId(),f.contractId(),service.getId(),new BigDecimal(amount)).activate());}
    private Map<String,Object> line(BillingAllocation a,String amount){return Map.of("billingAllocationId",a.getId(),"allocatedAmount",amount);}
    private Map<String,Object> payload(Fixture f,String number,List<Map<String,Object>> lines){return Map.of("contractId",f.contractId(),"number",number,"series","1","issuedOn","2026-11-01","items",lines);}
    private JsonNode register(Fixture f,String number,List<Map<String,Object>> lines,int expected)throws Exception{return call(f,post(BASE),payload(f,number,lines),expected);}
    private int code(Fixture f,String number,BillingAllocation a)throws Exception{return mvc.perform(post(BASE).with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload(f,number,List.of(line(a,"100.00")))))).andReturn().getResponse().getStatus();}
    private JsonNode read(Fixture f,BillingInstallment charge)throws Exception{return call(f,get(coveragePath(charge)),null,200);}
    private String coveragePath(BillingInstallment charge){return "/api/v1/company/billing-installments/"+charge.getId()+"/invoice-coverage";}
    private JsonNode group(JsonNode response,BillingAllocation a){for(var group:response.path("services"))if(group.path("contractServiceId").asText().equals(a.getContractServiceId().toString()))return group;throw new AssertionError("Group missing");}
    private JsonNode call(Fixture f,MockHttpServletRequestBuilder request,Object body,int expected)throws Exception{
        request.with(jwt().jwt(t->t.subject(f.userId().toString())));if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response=mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();if(expected>=400)assertTrue(response.getContentType().contains("application/problem+json"));
        return json.readTree(response.getContentAsString());}
    private JsonNode preview(Fixture f,byte[] bytes,int expected)throws Exception{
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart(BASE+"/xml/preview").file(new org.springframework.mock.web.MockMultipartFile("file","model.xml","application/xml",bytes))
                .param("contractId",f.contractId().toString()).with(jwt().jwt(t->t.subject(f.userId().toString())))).andExpect(status().is(expected)).andReturn().getResponse();
        if(expected==200)assertEquals("no-store",response.getHeader("Cache-Control"));return json.readTree(response.getContentAsString());}
    private byte[] synthetic(Fixture f){String text=new String(com.renovar.canteiro.io.receivables.application.NfseXmlPreviewParserTest.xml("2","100.00","100.00","900.00",""),java.nio.charset.StandardCharsets.UTF_8);
        return text.replace("12345678000190",companies.findById(f.companyId()).orElseThrow().getDocument()).getBytes(java.nio.charset.StandardCharsets.UTF_8);}
    private int count(String table,Fixture f){if(!List.of("invoice","change_request","tax_obligation","invoice_attachment").contains(table))throw new IllegalArgumentException();return jdbc.queryForObject("select count(*) from "+table+" where company_id=?",Integer.class,f.companyId());}
    private List<Map<String,Object>> snapshot(Fixture f){return jdbc.queryForList("select id,to_jsonb(i)::text as data from invoice i where company_id=? order by id",f.companyId());}
    private record Fixture(UUID companyId,UUID userId,UUID roleId,UUID contractId) { }
}
