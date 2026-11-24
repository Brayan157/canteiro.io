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
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReceivableSettlementHistoryApiIntegrationTest extends AbstractPostgresIntegrationTest {
    // Historical ledger regression fixtures exercise the former creation flow only in this test context.
    // The real fail-closed REST policy is covered without mocks in BillingReceiptsAndSettlementDateApiIntegrationTest.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private com.renovar.canteiro.io.receivables.application.ReceivableSettlementCreationPolicy legacyFixturePolicy;
    private static final String BASE = "/api/v1/company/receivables/";
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private ContractServiceRepository services;
    @Autowired private BillingAllocationRepository allocations;
    @Autowired private BillingInstallmentRepository installments;
    @Autowired private ReceivableRepository titles;
    @Autowired private ReceivableSettlementRepository settlements;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private PasswordHasher hasher;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test void readsOfficialPositiveReceiptsAndNegativeReversalsWithoutChangingBalancesOrExposingNotes() throws Exception {
        var f = fixture(CurrencyCode.USD, AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.CANCEL_DIRECT);
        var receipt = receive(f, "25.00", "PIX", 201).path("settlement");
        String original = receipt.path("id").asText();
        assertEquals("75.00", call(f, get(BASE + f.titleId() + "/balance"), null, 200).path("outstandingAmount").asText());
        var reversalResult = call(f, post(path(f) + "/" + original + "/reversal"), Map.of("reason", "private-bank-marker"), 201);
        String reversal = reversalResult.path("reversalId").asText();
        var before = snapshot(f);
        var page = call(f, get(path(f)), null, 200);
        assertEquals(2, page.path("totalItems").asInt()); assertEquals(20, page.path("size").asInt());
        JsonNode originalRow = row(page, original), reversedRow = row(page, reversal);
        assertEquals("SETTLEMENT", originalRow.path("entryType").asText()); assertEquals("Recebimento", originalRow.path("displayLabel").asText());
        assertEquals("CONFIRMED", originalRow.path("status").asText()); assertEquals(reversal, originalRow.path("reversedBySettlementId").asText());
        assertTrue(originalRow.path("reversalOfSettlementId").isNull()); assertEquals("25.00", originalRow.path("amount").asText());
        assertEquals("REVERSAL", reversedRow.path("entryType").asText()); assertEquals("Estorno de recebimento", reversedRow.path("displayLabel").asText());
        assertEquals(original, reversedRow.path("reversalOfSettlementId").asText()); assertTrue(reversedRow.path("reversedBySettlementId").isNull());
        assertEquals("-25.00", reversedRow.path("amount").asText());
        for (var item : page.path("items")) {
            assertEquals(f.titleId().toString(), item.path("receivableId").asText()); assertEquals("REC-HIST", item.path("receivableReference").asText());
            assertEquals(f.contractId().toString(), item.path("contractId").asText()); assertEquals("USD", item.path("currency").asText());
            assertTrue(item.path("amount").isString()); assertTrue(item.path("createdAt").asText().endsWith("Z"));
            for (String field : List.of("note", "reason", "bankAccount", "pixKey", "taxes", "beforeData", "afterData", "allocations", "outstandingAmount")) assertFalse(item.has(field));
        }
        assertFalse(page.toString().contains("private-bank-marker"));
        var originalOnly = call(f, get(path(f)).param("entryType", "SETTLEMENT"), null, 200);
        assertEquals(reversal, originalOnly.at("/items/0/reversedBySettlementId").asText());
        assertEquals(original, call(f, get(path(f)).param("entryType", "REVERSAL"), null, 200).at("/items/0/reversalOfSettlementId").asText());
        assertEquals("100.00", call(f, get(BASE + f.titleId() + "/balance"), null, 200).path("outstandingAmount").asText());
        assertEquals(before, snapshot(f));
    }

    @Test void requiresOnlyReadAndNeverGrantsBillingOrWritePermissions() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.CASH);
        assertEquals(1, call(f, get(path(f)), null, 200).path("totalItems").asInt());
        receive(f, "10.00", "PIX", 403);
        call(f, get("/api/v1/company/billing-installments/" + f.installmentId()), null, 403);
        var noRead = fixture(CurrencyCode.BRL);
        grant(noRead, AccessModule.BILLING, AccessAction.READ);
        call(noRead, get(path(noRead)), null, 403);
        mvc.perform(get(path(f))).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test void isolatesCountPagesAndExactSourcesEvenWhenAnotherTitleSharesTheContractAndService() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ);
        var other = fixture(CurrencyCode.BRL, AccessAction.READ);
        var first = save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        save(other, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        var siblingTitle = newTitle(f.companyId(), f.contractId(), "SIBLING", LocalDate.of(2026, 11, 1));
        var shared = allocations.findByBillingInstallmentIdAndCompanyId(f.installmentId(), f.companyId()).getFirst();
        allocations.save(BillingAllocation.createService(f.companyId(), siblingTitle.getBillingInstallmentId(), f.contractId(), shared.getContractServiceId(), new BigDecimal("100.00")).activate());
        var sibling = withTitle(f, siblingTitle);
        var siblingReceipt = save(sibling, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        var page = call(f, get(path(f)).param("companyId", other.companyId().toString()).param("size", "1"), null, 200);
        assertEquals(1, page.path("totalItems").asInt()); assertEquals(first.getId().toString(), page.at("/items/0/id").asText());
        assertEquals(siblingReceipt.getId().toString(), call(f, get(path(sibling)), null, 200).at("/items/0/id").asText());
        call(other, get(path(f)), null, 404); call(f, get(BASE + UUID.randomUUID() + "/settlements"), null, 404);
        assertThrows(DataIntegrityViolationException.class, () -> settlements.save(ReceivableSettlement.rehydrate(null,
                f.companyId(), sibling.titleId(), f.contractId(), LocalDate.of(2026, 10, 2), new BigDecimal("-10.00"),
                ReceivableSettlementEntryType.REVERSAL, first.getId(), ReceivableSettlementPaymentMethod.PIX, null,
                ReceivableSettlementStatus.CONFIRMED, null, null)));
        assertThrows(DataIntegrityViolationException.class, () -> settlements.save(ReceivableSettlement.confirm(f.companyId(), other.titleId(), f.contractId(),
                LocalDate.of(2026, 10, 1), BigDecimal.ONE, ReceivableSettlementPaymentMethod.PIX, null)));
        assertTrue(call(f, get(path(f)), null, 200).at("/items/0/reversedBySettlementId").isNull());
    }

    @Test void preservesHistoricalCancelledEntriesAndTitlesIncludingRenegotiatedParentsAndChildren() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.UPDATE_DIRECT);
        var receipt = receive(f, "25.00", "PIX", 201).path("settlement");
        call(f, post(BASE + f.titleId() + "/renegotiations"), Map.of("reason", "Reprogramação sintética", "installments",
                List.of(Map.of("dueOn", "2099-01-01", "amount", "75.00"))), 200);
        var before = snapshot(f);
        assertEquals(1, call(f, get(path(f)), null, 200).path("totalItems").asInt());
        assertEquals(receipt.path("id").asText(), call(f, get(path(f)), null, 200).at("/items/0/id").asText());
        assertEquals(before, snapshot(f));
        var cancelled = fixture(CurrencyCode.BRL, AccessAction.READ);
        var legacy = settlements.save(ReceivableSettlement.rehydrate(null, cancelled.companyId(), cancelled.titleId(), cancelled.contractId(),
                LocalDate.of(2026, 10, 1), new BigDecimal("10.00"), ReceivableSettlementEntryType.SETTLEMENT, null,
                ReceivableSettlementPaymentMethod.CHECK, "private-bank-marker", ReceivableSettlementStatus.CANCELLED, null, null));
        titles.updateStatus(titles.findByIdAndCompanyId(cancelled.titleId(), cancelled.companyId()).orElseThrow().cancel());
        var cancelledBefore = snapshot(cancelled);
        assertEquals(legacy.getId().toString(), call(cancelled, get(path(cancelled)), null, 200).at("/items/0/id").asText());
        assertEquals(1, call(cancelled, get(path(cancelled)).param("status", "CANCELLED"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(cancelled, get(path(cancelled)).param("status", "CONFIRMED"), null, 200).path("totalItems").asInt());
        assertEquals(cancelledBefore, snapshot(cancelled));
        var child = titles.findRenegotiatedChildren(f.titleId(), f.companyId()).getFirst();
        var childFixture = withTitle(f, child);
        assertEquals(0, call(f, get(path(childFixture)), null, 200).path("totalItems").asInt());
        call(childFixture, post(path(childFixture)), Map.of("receivedOn", child.getIssuedOn().toString(), "amount", "5.00", "paymentMethod", "BANK_TRANSFER"), 201);
        assertEquals(1, call(f, get(path(childFixture)), null, 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(path(f)), null, 200).path("totalItems").asInt());
    }

    @Test void filtersOnlyOfficialEnumsAndInclusiveBusinessDatesAndReturnsEmptyForValidTitles() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        assertEquals(0, call(f, get(path(f)), null, 200).path("totalItems").asInt());
        save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        save(f, "20.00", "2026-10-02", ReceivableSettlementPaymentMethod.CASH);
        for (var filter : List.of(Map.entry("paymentMethod", "PIX"), Map.entry("receivedFrom", "2026-10-02"), Map.entry("receivedTo", "2026-10-01"))) {
            assertEquals(1, call(f, get(path(f)).param(filter.getKey(), filter.getValue()), null, 200).path("totalItems").asInt());
        }
        assertEquals(1, call(f, get(path(f)).param("receivedFrom", "2026-10-01").param("receivedTo", "2026-10-01").param("paymentMethod", "PIX"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(path(f)).param("entryType", "REVERSAL"), null, 200).path("totalItems").asInt());
        assertEquals(2, call(f, get(path(f)).param("status", "CONFIRMED"), null, 200).path("totalItems").asInt());
    }

    @Test void paginatesStableDuplicatesWithExactIdsAndCreatedInstantsWithoutInferringReversals() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var a = save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        var b = save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        List<String> ids = new ArrayList<>(List.of(a.getId().toString(), b.getId().toString())); ids.sort(String::compareTo);
        for (int page = 0; page < 2; page++) {
            var result = call(f, get(path(f)).param("page", Integer.toString(page)).param("size", "1").param("sort", "receivedOn,asc"), null, 200);
            assertEquals(2, result.path("totalItems").asInt()); assertEquals(2, result.path("totalPages").asInt());
            assertEquals(ids.get(page), result.at("/items/0/id").asText());
            assertTrue(result.at("/items/0/reversalOfSettlementId").isNull()); assertTrue(result.at("/items/0/reversedBySettlementId").isNull());
            assertTrue(result.at("/items/0/createdAt").asText().endsWith("Z"));
        }
        assertEquals(b.getId().toString(), call(f, get(path(f)), null, 200).at("/items/0/id").asText());
        for (String sort : List.of("createdAt", "receivedOn", "entryType", "paymentMethod", "status")) {
            assertEquals(2, call(f, get(path(f)).param("sort", sort + ",desc", "receivedOn,asc"), null, 200).path("totalItems").asInt());
        }
        assertEquals(0, call(f, get(path(f)).param("page", "2").param("size", "1"), null, 200).path("items").size());
    }

    @Test void rejectsInvalidDatePaginationSortEnumsAndUuidUsingProblemDetails() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        for (var invalid : List.of(Map.entry("page", "-1"), Map.entry("size", "0"), Map.entry("size", "101"),
                Map.entry("page", "2147483647"), Map.entry("sort", "amount,asc"), Map.entry("sort", "note,asc"),
                Map.entry("sort", "createdAt,wrong"), Map.entry("sort", "createdAt"), Map.entry("entryType", "REFUND"),
                Map.entry("status", "REVERSED"), Map.entry("paymentMethod", "crypto"), Map.entry("receivedFrom", "today"))) {
            call(f, get(path(f)).param(invalid.getKey(), invalid.getValue()), null, 400);
        }
        call(f, get(path(f)).param("receivedFrom", "2026-11-02").param("receivedTo", "2026-11-01"), null, 400);
        call(f, get(BASE + "invalid/settlements"), null, 400);
    }

    @Test void approvalOnlyReceiptAndReversalNeverAppearAsOfficialEntries() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.REQUEST_CREATE, AccessAction.REQUEST_CANCEL);
        var proposal = receive(f, "10.00", "PIX", 202);
        assertTrue(proposal.path("changeRequestId").isString()); assertTrue(proposal.path("settlement").isNull());
        var before = snapshot(f);
        assertEquals(0, call(f, get(path(f)), null, 200).path("totalItems").asInt()); assertEquals(before, snapshot(f));
        var original = save(f, "10.00", "2026-10-01", ReceivableSettlementPaymentMethod.PIX);
        var reversal = call(f, post(path(f) + "/" + original.getId() + "/reversal"), Map.of("reason", "Proposta de estorno"), 202);
        assertTrue(reversal.path("changeRequestId").isString());
        var after = snapshot(f);
        var result = call(f, get(path(f)), null, 200);
        assertEquals(1, result.path("totalItems").asInt()); assertTrue(result.at("/items/0/reversedBySettlementId").isNull());
        assertEquals(after, snapshot(f));
    }

    @Test void preservesCanonicalPrecisionAndAllContractCurrencies() throws Exception {
        for (var currency : CurrencyCode.values()) {
            var f = fixture(currency, AccessAction.READ);
            var installment = installments.save(BillingInstallment.createApproved(f.companyId(), f.contractId(), "PRECISION", null, null, LocalDate.of(2026, 11, 1)));
            var title = titles.save(Receivable.create(f.companyId(), f.contractId(), installment.getId(), UUID.randomUUID(), null, null, null, LocalDate.of(2026, 11, 1), new BigDecimal("9007199254740993.01")));
            var precise = withTitle(f, title);
            var saved = save(precise, "9007199254740993.01", "2026-10-01", ReceivableSettlementPaymentMethod.OTHER);
            var result = call(f, get(path(precise)), null, 200).path("items").get(0);
            assertEquals(saved.getId().toString(), result.path("id").asText());
            assertEquals("9007199254740993.01", result.path("amount").asText()); assertTrue(result.path("amount").isString());
            assertEquals(currency.name(), result.path("currency").asText());
            assertTrue(result.path("receivableReference").isNull());
        }
    }

    @Test void exportsExecutableHistoryContractWithMoneyAndNullableExactLinks() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var root = json.readTree(specification);
        String path = "/api/v1/company/receivables/{receivableId}/settlements";
        assertEquals("#/components/schemas/PageResponseReceivableSettlementHistoryResponse", root.path("paths").path(path).at("/get/responses/200/content/application~1json/schema/$ref").asText());
        var props = root.at("/components/schemas/ReceivableSettlementHistoryResponse/properties");
        assertEquals("string", props.at("/amount/type").asText()); assertEquals("decimal", props.at("/amount/format").asText());
        for (String field : List.of("receivableReference", "reversalOfSettlementId", "reversedBySettlementId")) assertTrue(props.path(field).toString().contains("\"null\""));
        assertFalse(props.has("note")); assertEquals(12, root.at("/components/schemas/ReceivableSettlementHistoryResponse/required").size());
        String export = System.getenv("RECEIVABLE_HISTORY_OPENAPI_EXPORT_PATH");
        if (export != null && !export.isBlank()) {
            Path target = Path.of(export).toAbsolutePath().normalize(); Files.createDirectories(target.getParent()); Files.writeString(target, specification);
        }
    }

    private JsonNode row(JsonNode page, String id) {
        for (var row : page.path("items")) if (row.path("id").asText().equals(id)) return row;
        throw new AssertionError("Missing exact settlement id");
    }
    private JsonNode receive(Fixture f, String amount, String method, int status) throws Exception {
        return call(f, post(path(f)), Map.of("receivedOn", "2026-10-01", "amount", amount, "paymentMethod", method, "note", "private-bank-marker"), status);
    }
    private ReceivableSettlement save(Fixture f, String amount, String date, ReceivableSettlementPaymentMethod method) {
        return settlements.save(ReceivableSettlement.confirm(f.companyId(), f.titleId(), f.contractId(), LocalDate.parse(date), new BigDecimal(amount), method, "private-bank-marker"));
    }
    private JsonNode call(Fixture f, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        request.with(jwt().jwt(token -> token.subject(f.userId().toString())));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result = mvc.perform(request).andExpect(status().is(expected));
        if (expected == 200 && body == null) result.andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")));
        if (expected >= 400) result.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
    private List<Map<String, Object>> snapshot(Fixture f) {
        return jdbc.queryForList("""
                select 'title' kind,id::text,to_jsonb(r)::text data from receivable r where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=?
                union all select 'attribution',settlement_id::text||billing_allocation_id::text,to_jsonb(s)::text from receivable_settlement_allocation s where company_id=?
                union all select 'allocation',id::text,to_jsonb(a)::text from billing_allocation a where company_id=?
                union all select 'installment',id::text,to_jsonb(b)::text from billing_installment b where company_id=?
                union all select 'audit',id::text,to_jsonb(a)::text from audit_event a where company_id=?
                union all select 'request',id::text,to_jsonb(c)::text from change_request c where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }
    private Receivable newTitle(UUID company, UUID contract, String reference, LocalDate due) {
        var installment = installments.save(BillingInstallment.createApproved(company, contract, "PARC-HIST", null, null, due));
        return titles.save(Receivable.create(company, contract, installment.getId(), UUID.randomUUID(), reference, null, null, due, new BigDecimal("100.00")));
    }
    private Fixture withTitle(Fixture f, Receivable title) {
        return new Fixture(f.companyId(), f.userId(), f.roleId(), f.contractId(), title.getBillingInstallmentId(), title.getId());
    }
    private Fixture fixture(CurrencyCode currency, AccessAction... actions) {
        String suffix = UUID.randomUUID().toString();
        var company = companies.save(Company.create("Emitente sintética", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Cliente sintético", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Obra", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "HIST", ContractPricingMode.FIXED, null, "Contrato", ContractStatus.ACTIVE, null, null, null, currency));
        var title = newTitle(company.getId(), contract.getId(), "REC-HIST", LocalDate.of(2026, 11, 1));
        var service = services.save(ContractService.create(company.getId(), contract.getId(), "Serviço", null, ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal("100.00")));
        allocations.save(BillingAllocation.createService(company.getId(), title.getBillingInstallmentId(), contract.getId(), service.getId(), new BigDecimal("100.00")).activate());
        User user = User.create(suffix + "@example.test", UserType.COMPANY); user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), company.getId()));
        var role = roles.save(Role.create(company.getId(), "Operador", null)); userRoles.save(UserRole.create(user.getId(), role.getId(), company.getId()));
        var f = new Fixture(company.getId(), user.getId(), role.getId(), contract.getId(), title.getBillingInstallmentId(), title.getId());
        for (AccessAction action : actions) grant(f, AccessModule.RECEIVABLES, action);
        return f;
    }
    private void grant(Fixture f, AccessModule module, AccessAction action) {
        var permission = permissions.findByModuleAndAction(module, action).orElseThrow(); rolePermissions.save(RolePermission.create(f.roleId(), permission.getId()));
    }
    private String path(Fixture f) { return BASE + f.titleId() + "/settlements"; }
    private record Fixture(UUID companyId, UUID userId, UUID roleId, UUID contractId, UUID installmentId, UUID titleId) { }
}
