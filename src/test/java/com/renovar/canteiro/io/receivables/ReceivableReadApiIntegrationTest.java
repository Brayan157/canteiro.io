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
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReceivableReadApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BASE = "/api/v1/company/receivables";
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private BillingInstallmentRepository installments;
    @Autowired private ReceivableRepository receivables;
    @Autowired private ReceivableSettlementRepository settlements;
    @Autowired private com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository origins;
    @Autowired private com.renovar.canteiro.io.receivables.domain.TaxObligationRepository taxes;
    @Autowired private com.renovar.canteiro.io.receivables.domain.InvoiceRepository invoices;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private PasswordHasher hasher;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    @Test void readsCanonicalHistoricalFaceValueAndReadableContextWithOnlyReceivablesRead() throws Exception {
        for (var currency : CurrencyCode.values()) {
            var f = fixture(currency, AccessAction.READ);
            var before = snapshot(f);
            var page = call(f, get(BASE), null, 200);
            assertEquals(1, page.path("totalItems").asInt()); assertEquals(20, page.path("size").asInt());
            var item = page.path("items").get(0);
            assertEquals(f.titleId().toString(), item.path("id").asText());
            assertEquals("REC-01", item.path("reference").asText());
            assertEquals("Contrato Usina", item.path("contractName").asText());
            assertEquals("CTR-REF", item.path("contractReference").asText());
            assertEquals("0001/2027", item.path("contractIdentifier").asText());
            assertEquals("Fábrica de açúcar", item.path("workName").asText());
            assertEquals("OB-REF", item.path("workReference").asText());
            assertEquals("Cliente sintético", item.path("finalCustomerName").asText());
            assertEquals("PARC-01", item.path("billingInstallmentReference").asText());
            assertEquals("100.00", item.path("originalAmount").asText()); assertTrue(item.path("originalAmount").isString());
            assertEquals(currency.name(), item.path("currency").asText());
            assertEquals("2026-11-25", item.path("dueOn").asText()); assertTrue(item.path("issuedOn").isNull());
            assertTrue(item.path("renegotiationSourceReceivableId").isNull());
            for (String field : List.of("document", "phone", "email", "accessKey", "storageKey", "taxes", "settlements", "allocations", "outstandingAmount", "description")) assertFalse(item.has(field));
            var detail = call(f, get(BASE + "/" + f.titleId()), null, 200);
            assertEquals(item, detail.path("receivable")); assertTrue(detail.path("description").isNull());
            call(f, get("/api/v1/company/billing-installments/" + f.installmentId()), null, 403);
            call(f, get("/api/v1/company/contracts/" + f.contractId()), null, 403);
            assertEquals(before, snapshot(f));
        }
    }

    @Test void isolatesListCountFiltersPaginationDetailAndBalanceBetweenCompanies() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var foreign = fixture(CurrencyCode.USD, AccessAction.READ);
        title(f, "Outra parcela", LocalDate.of(2026, 12, 1));
        assertEquals(2, call(f, get(BASE).param("companyId", foreign.companyId().toString()), null, 200).path("totalItems").asInt());
        for (var filter : Map.of("contractId", foreign.contractId(), "workId", foreign.workId(), "finalCustomerId", foreign.customerId()).entrySet()) {
            assertEquals(0, call(f, get(BASE).param(filter.getKey(), filter.getValue().toString()), null, 200).path("totalItems").asInt());
        }
        assertEquals(0, call(f, get(BASE).param("currency", "USD"), null, 200).path("totalItems").asInt());
        call(f, get(BASE + "/" + foreign.titleId()), null, 404);
        call(f, get(BASE + "/" + UUID.randomUUID()), null, 404);
        call(f, get(BASE + "/" + foreign.titleId() + "/balance"), null, 404);
        assertEquals(1, call(foreign, get(BASE).param("size", "1"), null, 200).path("totalItems").asInt());
    }

    @Test void appliesLiteralBusinessFiltersAndNeverSearchesCustomerDocumentsOrDescriptions() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ);
        title(f, "100%_! sinal", LocalDate.of(2026, 12, 2));
        for (var filter : Map.of("reference", "rec-01", "contractSearch", "0001/2027", "workSearch", "OB-REF",
                "customerName", "cliente", "billingReference", "parc-01", "search", "Fábrica").entrySet()) {
            int expected = Set.of("reference", "billingReference").contains(filter.getKey()) ? 1 : 2;
            assertEquals(expected, call(f, get(BASE).param(filter.getKey(), filter.getValue()), null, 200).path("totalItems").asInt(), filter.getKey());
        }
        for (String literal : List.of("%", "_", "!", "100%_!")) assertEquals(1, call(f, get(BASE).param("search", literal), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE).param("search", "12345678000195"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE).param("search", "secret-bank-marker"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE).param("search", "' OR 1=1 --"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE).param("reference", "REC-01").param("currency", "USD"), null, 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(BASE).param("dueFrom", "2026-11-25").param("dueTo", "2026-11-25"), null, 200).path("totalItems").asInt());
    }

    @Test void paginatesAndSortsPredictablyWithIdTieBreakersAndNullsLast() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var second = title(f, "REC-02", LocalDate.of(2026, 11, 25));
        var third = title(f, null, LocalDate.of(2026, 12, 10));
        List<String> expected = new ArrayList<>(List.of(f.titleId().toString(), second.getId().toString())); expected.sort(String::compareTo); expected.add(third.getId().toString());
        for (int page = 0; page < 3; page++) {
            var result = call(f, get(BASE).param("page", Integer.toString(page)).param("size", "1"), null, 200);
            assertEquals(3, result.path("totalItems").asInt()); assertEquals(3, result.path("totalPages").asInt());
            assertEquals(expected.get(page), result.path("items").get(0).path("id").asText());
        }
        assertEquals(third.getId().toString(), call(f, get(BASE).param("sort", "dueOn,desc"), null, 200).path("items").get(0).path("id").asText());
        assertEquals(third.getId().toString(), call(f, get(BASE).param("sort", "reference,asc"), null, 200).path("items").get(2).path("id").asText());
        for (String field : List.of("issuedOn", "status", "createdAt", "contractName", "workName", "finalCustomerName")) {
            assertEquals(3, call(f, get(BASE).param("sort", field + ",asc", "dueOn,desc"), null, 200).path("totalItems").asInt());
        }
        assertEquals(0, call(f, get(BASE).param("page", "3").param("size", "1"), null, 200).path("items").size());
    }

    @Test void rejectsInvalidPagesFiltersAndSortsWithCorrelatedProblemDetails() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        for (var invalid : List.of(Map.entry("page", "-1"), Map.entry("size", "0"), Map.entry("size", "101"),
                Map.entry("page", "2147483647"), Map.entry("sort", "originalAmount,asc"), Map.entry("sort", "document,asc"),
                Map.entry("sort", "dueOn,wrong"), Map.entry("sort", "dueOn"), Map.entry("status", "PAID"), Map.entry("currency", "BTC"),
                Map.entry("contractId", "invalid"), Map.entry("dueFrom", "tomorrow"), Map.entry("search", "x".repeat(201)),
                Map.entry("reference", "x".repeat(101)), Map.entry("billingReference", "x".repeat(101)),
                Map.entry("contractSearch", "x".repeat(201)), Map.entry("workSearch", "x".repeat(201)), Map.entry("customerName", "x".repeat(201)))) {
            call(f, get(BASE).param(invalid.getKey(), invalid.getValue()), null, 400);
        }
        call(f, get(BASE).param("dueFrom", "2026-12-01").param("dueTo", "2026-11-01"), null, 400);
        call(f, get(BASE + "/invalid"), null, 400);
    }

    @Test void deniesUnauthenticatedAndUsersWithoutReceivablesReadEvenIfTheyHaveBillingRead() throws Exception {
        var f = fixture(CurrencyCode.BRL);
        grant(f, AccessModule.BILLING, AccessAction.READ);
        for (String path : List.of(BASE, BASE + "/" + f.titleId())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.correlationId").isNotEmpty());
            call(f, get(path), null, 403);
        }
    }

    @Test void preservesRenegotiatedParentCancelledChildrenAndExactLineageWithoutCountingProposals() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ, AccessAction.UPDATE_DIRECT, AccessAction.CANCEL_DIRECT);
        call(f, post(BASE + "/" + f.titleId() + "/renegotiations"), renegotiation(), 200);
        var page = call(f, get(BASE).param("sort", "dueOn,asc"), null, 200);
        assertEquals(3, page.path("totalItems").asInt());
        assertEquals(2, call(f, get(BASE).param("financialView", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        assertEquals("RENEGOTIATED", call(f, get(BASE + "/" + f.titleId()), null, 200).at("/receivable/status").asText());
        assertEquals(1, call(f, get(BASE).param("status", "RENEGOTIATED"), null, 200).path("totalItems").asInt());
        List<String> children = new ArrayList<>();
        for (var row : page.path("items")) if (!row.path("id").asText().equals(f.titleId().toString())) {
            children.add(row.path("id").asText());
            assertEquals(f.titleId().toString(), row.path("renegotiationSourceReceivableId").asText());
            assertEquals("REC-01", row.path("renegotiationSourceReference").asText());
            assertEquals(f.installmentId().toString(), row.path("billingInstallmentId").asText());
            assertEquals("EUR", row.path("currency").asText());
            assertEquals(row, call(f, get(BASE + "/" + row.path("id").asText()), null, 200).path("receivable"));
        }
        call(f, post(BASE + "/" + f.titleId() + "/renegotiations/reversal"), Map.of("reason", "Reversão sintética"), 200);
        var before = snapshot(f);
        assertEquals(2, call(f, get(BASE).param("status", "CANCELLED"), null, 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(BASE).param("financialView", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        for (String id : children) assertEquals("CANCELLED", call(f, get(BASE + "/" + id), null, 200).at("/receivable/status").asText());
        assertEquals(before, snapshot(f));
        var pending = fixture(CurrencyCode.USD, AccessAction.READ, AccessAction.REQUEST_UPDATE);
        var proposal = call(pending, post(BASE + "/" + pending.titleId() + "/renegotiations"), renegotiation(), 202);
        assertTrue(proposal.path("changeRequestId").isString());
        var pendingBefore = snapshot(pending);
        assertEquals(1, call(pending, get(BASE), null, 200).path("totalItems").asInt());
        assertEquals(1, call(pending, get(BASE).param("financialView", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        assertEquals("OPEN", call(pending, get(BASE + "/" + pending.titleId()), null, 200).at("/receivable/status").asText());
        call(pending, get(BASE + "/" + proposal.path("changeRequestId").asText()), null, 404);
        assertEquals(pendingBefore, snapshot(pending));
    }

    @Test void keepsBalanceAuthorityForPartialReceiptsAndOverdueWithoutMutatingPersistedStatus() throws Exception {
        var f = fixture(CurrencyCode.USD, AccessAction.READ);
        var overdue = title(f, "Vencido", LocalDate.of(2020, 1, 1));
        settlements.save(ReceivableSettlement.confirm(f.companyId(), overdue.getId(), f.contractId(), LocalDate.of(2020, 1, 1),
                new BigDecimal("25.00"), ReceivableSettlementPaymentMethod.PIX, "secret-bank-marker"));
        var before = snapshot(f);
        var detail = call(f, get(BASE + "/" + overdue.getId()), null, 200);
        assertEquals("OPEN", detail.at("/receivable/status").asText());
        assertEquals("100.00", detail.at("/receivable/originalAmount").asText());
        assertFalse(detail.toString().contains("secret-bank-marker"));
        var balance = call(f, get(BASE + "/" + overdue.getId() + "/balance"), null, 200);
        assertEquals("OVERDUE", balance.path("status").asText());
        assertEquals("75.00", balance.path("outstandingAmount").asText());
        assertEquals(0, call(f, get(BASE).param("status", "OVERDUE"), null, 200).path("totalItems").asInt());
        assertEquals(before, snapshot(f));
    }

    @Test void filtersOfficialPositionBeforeCountAndPaginationAndNetsReversals() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var partial = title(f, "Parcial", LocalDate.of(2026, 12, 1));
        var paid = title(f, "Recebido", LocalDate.of(2026, 12, 2));
        var reversed = title(f, "Estornado", LocalDate.of(2026, 12, 3));
        receipt(f, partial.getId(), "25.00"); receipt(f, paid.getId(), "100.00");
        var original = receipt(f, reversed.getId(), "100.00");
        settlements.save(ReceivableSettlement.reverse(original, LocalDate.of(2026, 10, 27), "Estorno sintético"));
        var cancelled = title(f, "Cancelado", LocalDate.of(2026, 12, 4)); receivables.updateStatus(cancelled.cancel());
        var foreign = fixture(CurrencyCode.EUR, AccessAction.READ); receipt(foreign, foreign.titleId(), "100.00");
        var before = snapshot(f);
        for (int page = 0; page < 3; page++) {
            var result = call(f, get(BASE).param("financialView", "OUTSTANDING").param("size", "1").param("page", "" + page), null, 200);
            assertEquals(3, result.path("totalItems").asInt()); assertEquals(3, result.path("totalPages").asInt());
            assertEquals(1, result.path("items").size());
        }
        var partialRow = call(f, get(BASE + "/" + partial.getId()), null, 200).at("/receivable/financialPosition");
        assertEquals("PARTIALLY_RECEIVED", partialRow.path("status").asText());
        assertEquals("25.00", partialRow.path("confirmedSettlementAmount").asText());
        assertEquals("75.00", partialRow.path("outstandingAmount").asText());
        assertEquals("75.00", call(f, get(BASE + "/" + partial.getId() + "/balance"), null, 200).path("outstandingAmount").asText());
        var received = call(f, get(BASE).param("financialView", "RECEIVED"), null, 200);
        assertEquals(1, received.path("totalItems").asInt()); assertEquals(paid.getId().toString(), received.at("/items/0/id").asText());
        assertEquals("RECEIVED", received.at("/items/0/financialPosition/status").asText());
        assertEquals("0.00", received.at("/items/0/financialPosition/outstandingAmount").asText());
        assertEquals(5, call(f, get(BASE).param("financialView", "ALL"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE).param("financialView", "RECEIVED").param("contractId", foreign.contractId().toString()), null, 200).path("totalItems").asInt());
        call(f, get(BASE).param("financialView", "PAID"), null, 400);
        assertEquals(before, snapshot(f));
    }

    @Test void separatesCreditsAndWithholdingFromMoneyAndPreservesReversedAdjustments() throws Exception {
        var f = fixture(CurrencyCode.USD, AccessAction.READ);
        var credit = saveCredit(new com.renovar.canteiro.io.receivables.domain.ReceivableCredit(null, f.companyId(),
                f.titleId(), f.contractId(), new BigDecimal("100.00"), "Crédito sintético", null, f.userId(), null));
        var withheld = title(f, "Retido", LocalDate.of(2026, 12, 1));
        var invoice = invoices.save(com.renovar.canteiro.io.receivables.domain.Invoice.register(f.companyId(), f.contractId(),
                "NF-teste", null, null, LocalDate.of(2026, 10, 27), new BigDecimal("100.00")));
        var tax = taxes.save(com.renovar.canteiro.io.receivables.domain.TaxObligation.register(f.companyId(), invoice.getId(),
                f.contractId(), withheld.getId(), "Retenção sintética", null, null, new BigDecimal("100.00"),
                com.renovar.canteiro.io.receivables.domain.TaxTreatment.WITHHELD_BY_CUSTOMER));
        var mixed = title(f, "Misto", LocalDate.of(2026, 12, 2));
        saveCredit(new com.renovar.canteiro.io.receivables.domain.ReceivableCredit(null, f.companyId(), mixed.getId(),
                f.contractId(), new BigDecimal("20.00"), "Crédito sintético", null, f.userId(), null)); receipt(f, mixed.getId(), "80.00");
        var before = snapshot(f);
        assertEquals(0, call(f, get(BASE).param("financialView", "OUTSTANDING"), null, 200).path("totalItems").asInt());
        var received = call(f, get(BASE).param("financialView", "RECEIVED"), null, 200);
        assertEquals(1, received.path("totalItems").asInt()); assertEquals(mixed.getId().toString(), received.at("/items/0/id").asText());
        assertEquals("SETTLED_WITH_ADJUSTMENTS", received.at("/items/0/financialPosition/status").asText());
        assertEquals("80.00", received.at("/items/0/financialPosition/confirmedSettlementAmount").asText());
        for (UUID id : List.of(f.titleId(), withheld.getId())) {
            var row = call(f, get(BASE + "/" + id), null, 200).at("/receivable/financialPosition");
            assertEquals("SETTLED_WITH_ADJUSTMENTS", row.path("status").asText());
            assertEquals("0.00", row.path("confirmedSettlementAmount").asText());
        }
        assertEquals(before, snapshot(f));
        saveCredit(new com.renovar.canteiro.io.receivables.domain.ReceivableCredit(null, f.companyId(), f.titleId(),
                f.contractId(), new BigDecimal("100.00"), "Reversão de crédito", credit.id(), f.userId(), null));
        taxes.updateStatus(tax.cancel());
        assertEquals(2, call(f, get(BASE).param("financialView", "OUTSTANDING"), null, 200).path("totalItems").asInt());
    }

    private ReceivableSettlement receipt(Fixture f, UUID titleId, String amount) {
        return settlements.save(ReceivableSettlement.confirm(f.companyId(), titleId, f.contractId(), LocalDate.of(2026, 10, 27),
                new BigDecimal(amount), ReceivableSettlementPaymentMethod.PIX, "Somente fixture sintética"));
    }

    private com.renovar.canteiro.io.receivables.domain.ReceivableCredit saveCredit(
            com.renovar.canteiro.io.receivables.domain.ReceivableCredit credit) {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager)
                .execute(status -> origins.saveCredit(credit));
    }

    @Test void exportsExecutableReadContractWithCanonicalMoneyAndNoSensitiveFields() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var root = json.readTree(specification);
        assertEquals("#/components/schemas/PageResponseReceivableSummaryResponse", root.path("paths").path(BASE).at("/get/responses/200/content/application~1json/schema/$ref").asText());
        assertEquals("#/components/schemas/ReceivableDetailsResponse", root.path("paths").path(BASE + "/{receivableId}").at("/get/responses/200/content/application~1json/schema/$ref").asText());
        var props = root.at("/components/schemas/ReceivableSummaryResponse/properties");
        assertEquals("string", props.at("/originalAmount/type").asText()); assertEquals("decimal", props.at("/originalAmount/format").asText());
        assertTrue(props.path("renegotiationSourceReceivableId").toString().contains("\"null\""));
        assertTrue(props.path("issuedOn").toString().contains("\"null\""));
        assertFalse(props.has("settlements")); assertFalse(props.has("document")); assertFalse(props.has("outstandingAmount"));
        assertEquals("#/components/schemas/ReceivableFinancialPositionResponse", props.at("/financialPosition/$ref").asText());
        var position = root.at("/components/schemas/ReceivableFinancialPositionResponse/properties");
        for (String field : List.of("withheldAmount", "creditedAmount", "netReceivableAmount", "confirmedSettlementAmount", "outstandingAmount")) {
            assertEquals("string", position.path(field).path("type").asText()); assertEquals("decimal", position.path(field).path("format").asText());
        }
        String export = System.getenv("RECEIVABLE_READ_OPENAPI_EXPORT_PATH");
        if (export != null && !export.isBlank()) {
            Path target = Path.of(export).toAbsolutePath().normalize(); Files.createDirectories(target.getParent()); Files.writeString(target, specification);
        }
    }

    private Map<String, Object> renegotiation() {
        return Map.of("reason", "Reprogramação sintética", "installments", List.of(Map.of("dueOn", "2099-01-01", "amount", "40.00"), Map.of("dueOn", "2099-02-01", "amount", "60.00")));
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
                union all select 'installment',id::text,to_jsonb(b)::text from billing_installment b where company_id=?
                union all select 'allocation',id::text,to_jsonb(a)::text from billing_allocation a where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=?
                union all select 'tax',id::text,to_jsonb(t)::text from tax_obligation t where company_id=?
                union all select 'credit',id::text,to_jsonb(cr)::text from receivable_credit cr where company_id=?
                union all select 'audit',id::text,to_jsonb(a)::text from audit_event a where company_id=?
                union all select 'request',id::text,to_jsonb(c)::text from change_request c where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }

    private Receivable title(Fixture f, String reference, LocalDate dueOn) {
        var installment = installments.save(BillingInstallment.createApproved(f.companyId(), f.contractId(), reference, null, null, dueOn));
        return receivables.save(Receivable.create(f.companyId(), f.contractId(), installment.getId(), UUID.randomUUID(), reference,
                "Serviço executado", null, dueOn, new BigDecimal("100.00")));
    }

    private Fixture fixture(CurrencyCode currency, AccessAction... actions) {
        String suffix = UUID.randomUUID().toString();
        var company = companies.save(Company.create("Emitente sintética", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Cliente sintético", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Fábrica de açúcar", "OB-REF", WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "0001/2027", ContractPricingMode.FIXED, "CTR-REF", "Contrato Usina", ContractStatus.ACTIVE, null, null, null, currency));
        var installment = installments.save(BillingInstallment.createApproved(company.getId(), contract.getId(), "PARC-01", null, null, LocalDate.of(2026, 11, 25)));
        var title = receivables.save(Receivable.create(company.getId(), contract.getId(), installment.getId(), UUID.randomUUID(), "REC-01", null, null, LocalDate.of(2026, 11, 25), new BigDecimal("100.00")));
        User user = User.create(suffix + "@example.test", UserType.COMPANY); user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), company.getId()));
        var role = roles.save(Role.create(company.getId(), "Operador", null)); userRoles.save(UserRole.create(user.getId(), role.getId(), company.getId()));
        var fixture = new Fixture(company.getId(), user.getId(), role.getId(), customer.getId(), work.getId(), contract.getId(), installment.getId(), title.getId());
        for (AccessAction action : actions) grant(fixture, AccessModule.RECEIVABLES, action);
        return fixture;
    }

    private void grant(Fixture f, AccessModule module, AccessAction action) {
        var permission = permissions.findByModuleAndAction(module, action).orElseThrow(); rolePermissions.save(RolePermission.create(f.roleId(), permission.getId()));
    }

    private record Fixture(UUID companyId, UUID userId, UUID roleId, UUID customerId, UUID workId, UUID contractId, UUID installmentId, UUID titleId) { }
}
