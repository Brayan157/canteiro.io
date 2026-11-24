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
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class InvoiceReadApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BASE = "/api/v1/company/invoices";
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private ContractServiceRepository services;
    @Autowired private InvoiceRepository invoices;
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

    @Test void returnsCanonicalPersistedAmountsAndCancelledHistoryWithoutFiscalKeys() throws Exception {
        var f = fixture(true, CurrencyCode.USD);
        Invoice cancelled = invoice(f, "NF-cancelada", null, "2026-10-21");
        invoices.updateStatus(cancelled.cancel());
        JsonNode list = call(f, get(BASE), 200);
        assertEquals(2, list.path("totalItems").asInt());
        assertEquals("USD", list.at("/items/0/currency").asText());
        assertEquals("99.90", list.at("/items/0/totalAmount").asText());
        assertTrue(list.at("/items/0/totalAmount").isString());
        assertEquals("Contrato usina", list.at("/items/0/contractName").asText());
        assertEquals("CANCELLED", list.at("/items/1/status").asText());
        assertFalse(list.toString().contains("accessKey"));
        assertFalse(list.toString().contains("private-fiscal-key"));
        assertFalse(list.toString().contains("storage"));
        assertEquals("CANCELLED", call(f, get(BASE + "/" + cancelled.getId()), 200).at("/invoice/status").asText());
    }

    @Test void paginatesWithStableTieBreakAndLiteralBusinessFilters() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        Invoice second = invoice(f, "NF%_!2", "Serie_!%", "2026-10-25");
        invoice(f, "NF-3", "Outra", "2026-10-24");
        var all = call(f, get(BASE), 200);
        assertEquals(3, all.path("totalItems").asInt());
        for (int page = 0; page < 3; page++) {
            var result = call(f, get(BASE).param("page", Integer.toString(page)).param("size", "1"), 200);
            assertEquals(all.path("items").get(page).path("id"), result.at("/items/0/id"));
            assertEquals(all.path("items").get(page).path("totalAmount"), result.at("/items/0/totalAmount"));
            assertEquals(3, result.path("totalPages").asInt());
        }
        assertTrue(all.at("/items/0/id").asText().compareTo(all.at("/items/1/id").asText()) < 0);
        assertEquals(0, call(f, get(BASE).param("page", "3").param("size", "1"), 200).path("items").size());
        assertEquals(second.getId().toString(), call(f, get(BASE).param("number", "%_!"), 200).at("/items/0/id").asText());
        assertEquals(1, call(f, get(BASE).param("series", "serie_!%"), 200).path("totalItems").asInt());
        for (String search : List.of("USINA", "REF-C", "0001/2027")) {
            assertEquals(3, call(f, get(BASE).param("contractSearch", search), 200).path("totalItems").asInt());
        }
        assertEquals(0, call(f, get(BASE).param("contractSearch", "%"), 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(BASE).param("issuedFrom", "2026-10-24").param("issuedTo", "2026-10-24"), 200).path("totalItems").asInt());
        assertEquals("2026-10-24", call(f, get(BASE).param("sort", "issuedOn,asc"), 200).at("/items/0/issuedOn").asText());
        assertEquals(0, call(f, get(BASE).param("status", "CANCELLED"), 200).path("totalItems").asInt());
        assertEquals(3, call(f, get(BASE).param("sort", "series,asc", "number,desc"), 200).path("items").size());
    }

    @Test void validatesParametersWithCorrelatedProblemDetails() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        for (String[] invalid : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"},
                {"sort", "totalAmount,asc"}, {"sort", "issuedOn,invalid"}, {"sort", "issuedOn"}, {"contractId", "invalid"},
                {"issuedFrom", "2026-99-01"}, {"status", "PAID"}, {"number", "x".repeat(101)}, {"series", "x".repeat(51)},
                {"contractSearch", "x".repeat(201)}, {"installmentReference", "x".repeat(101)}}) {
            assertFalse(call(f, get(BASE).param(invalid[0], invalid[1]), 400).path("correlationId").asText().isBlank());
        }
        call(f, get(BASE).param("issuedFrom", "2026-10-25").param("issuedTo", "2026-10-24"), 400);
        call(f, get(BASE + "/invalid"), 400);
    }

    @Test void requiresAuthenticationAndInvoiceReadEvenIfUserCanCreate() throws Exception {
        for (String path : List.of(BASE, BASE + "/" + UUID.randomUUID())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("UNAUTHENTICATED")).andExpect(jsonPath("$.correlationId").isNotEmpty());
            mvc.perform(get(path).header("Authorization", "Bearer private-invalid-test-token"))
                    .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-invalid-test-token"))));
        }
        var f = fixture(false, CurrencyCode.BRL);
        grant(f, AccessAction.CREATE_DIRECT);
        call(f, get(BASE), 403);
        call(f, get(BASE + "/" + f.invoiceId()), 403);
    }

    @Test void isolatesForeignInvoiceContractFiltersAndReferences() throws Exception {
        var owner = fixture(true, CurrencyCode.BRL);
        var other = fixture(true, CurrencyCode.EUR);
        call(other, get(BASE + "/" + owner.invoiceId()), 404);
        call(other, get(BASE + "/" + UUID.randomUUID()), 404);
        assertEquals(0, call(other, get(BASE).param("contractId", owner.contractId().toString()), 200).path("totalItems").asInt());
        assertEquals(1, call(other, get(BASE).param("companyId", owner.companyId().toString()), 200).path("totalItems").asInt());
        assertEquals("EUR", call(other, get(BASE), 200).at("/items/0/currency").asText());
        var allocation = allocation(owner, false);
        items.save(InvoiceItem.create(owner.companyId(), owner.invoiceId(), owner.contractId(), allocation.getId(), allocation.getContractServiceId(), null, new BigDecimal("99.90")));
        assertEquals(1, call(owner, get(BASE).param("installmentReference", "PARC%_!"), 200).path("totalItems").asInt());
        assertEquals(0, call(other, get(BASE).param("installmentReference", "PARC%_!"), 200).path("totalItems").asInt());
    }

    @Test void readsSignedItemsAndPreservesFinancialStateAuditAndCancelledItems() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        var positive = allocation(f, false);
        var discount = allocation(f, true);
        var first = items.save(InvoiceItem.create(f.companyId(), f.invoiceId(), f.contractId(), positive.getId(), positive.getContractServiceId(), "Serviço", new BigDecimal("100.00")));
        var negative = items.save(InvoiceItem.create(f.companyId(), f.invoiceId(), f.contractId(), discount.getId(), null, null, new BigDecimal("-0.10")));
        items.updateStatus(first.cancel()); items.updateStatus(negative.cancel());
        invoices.updateStatus(invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().cancel());
        var before = snapshot(f);
        var detail = call(f, get(BASE + "/" + f.invoiceId()), 200);
        assertEquals("99.90", detail.at("/invoice/totalAmount").asText());
        assertEquals(2, detail.path("items").size());
        for (JsonNode line : detail.path("items")) {
            assertEquals("CANCELLED", line.path("status").asText());
            assertTrue(line.path("measurementContractCompositionId").isNull());
            assertTrue(line.path("allocatedAmount").isString());
            if (line.path("id").asText().equals(negative.getId().toString())) {
                assertEquals("-0.10", line.path("allocatedAmount").asText());
                assertTrue(line.path("contractServiceId").isNull());
            } else {
                assertEquals("Montagem", line.path("contractServiceName").asText());
            }
        }
        call(f, get(BASE).param("installmentReference", "PARC%_!"), 200);
        assertEquals(before, snapshot(f));
        for (String field : List.of("accessKey", "storageKey", "attachments", "taxObligations", "receivedAmount")) assertFalse(detail.has(field));
    }

    @Test void pendingCreationDoesNotBecomeAnOfficialInvoice() throws Exception {
        var f = fixture(true, CurrencyCode.BRL); grant(f, AccessAction.REQUEST_CREATE);
        var allocation = allocation(f, false);
        mvc.perform(post(BASE).with(jwt().jwt(token -> token.subject(f.userId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("contractId", f.contractId(),
                        "number", "PENDING-NF", "issuedOn", "2026-10-25", "items", List.of(Map.of("billingAllocationId", allocation.getId(), "allocatedAmount", "10.00"))))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.changeRequestId").isNotEmpty());
        assertEquals(0, call(f, get(BASE).param("number", "PENDING-NF"), 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(BASE), 200).path("totalItems").asInt());
        var proposedContract = extraContract(f, "Somente proposta", "PENDING-C");
        var target = new Fixture(f.companyId(), f.userId(), f.roleId(), proposedContract.getId(), null);
        var proposedAllocation = allocation(target, false);
        mvc.perform(post(BASE).with(jwt().jwt(token -> token.subject(f.userId().toString())))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("contractId", proposedContract.getId(),
                        "number", "PENDING-ONLY-GROUP", "issuedOn", "2026-10-25", "items", List.of(Map.of("billingAllocationId", proposedAllocation.getId(), "allocatedAmount", "10.00"))))))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.changeRequestId").isNotEmpty());
        assertEquals(1, call(f, get(BASE + "/contracts"), 200).path("totalItems").asInt());
        call(f, get(BASE + "/contracts/" + proposedContract.getId()), 404);
    }

    @Test void exportsExecutableInvoiceReadContract() throws Exception {
        String text = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andReturn().getResponse().getContentAsString();
        var root = json.readTree(text);
        assertEquals("#/components/schemas/PageResponseInvoiceSummaryResponse", root.path("paths").path(BASE).at("/get/responses/200/content/application~1json/schema/$ref").asText());
        assertEquals("#/components/schemas/InvoiceDetailsResponse", root.path("paths").path(BASE + "/{invoiceId}").at("/get/responses/200/content/application~1json/schema/$ref").asText());
        assertEquals("string", root.at("/components/schemas/InvoiceSummaryResponse/properties/totalAmount/type").asText());
        assertEquals("decimal", root.at("/components/schemas/InvoiceReadItemResponse/properties/allocatedAmount/format").asText());
        assertTrue(root.at("/components/schemas/InvoiceReadItemResponse/properties/measurementContractCompositionId").toString().contains("\"null\""));
        assertFalse(root.at("/components/schemas/InvoiceSummaryResponse/properties").has("accessKey"));
        assertEquals("#/components/schemas/InvoiceReceiptPositionResponse", root.at("/components/schemas/InvoiceSummaryResponse/properties/receiptPosition/$ref").asText());
        for (String field : List.of("receivedAmount", "outstandingAmount")) {
            assertTrue(root.at("/components/schemas/InvoiceReceiptPositionResponse/properties/" + field).toString().contains("\"null\""));
        }
        String export = System.getenv("INVOICE_READ_OPENAPI_EXPORT_PATH");
        if (export != null && !export.isBlank()) {
            Path target = Path.of(export).toAbsolutePath().normalize(); Files.createDirectories(target.getParent()); Files.writeString(target, text);
        }
    }

    @Test void returnsHonestUnavailableReceiptPositionWithoutInferringCashFromTheContract() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        var before = snapshot(f);
        for (String path : List.of(BASE, BASE + "/" + f.invoiceId())) {
            var result = call(f, get(path), 200);
            var position = result.at(path.equals(BASE) ? "/items/0/receiptPosition" : "/invoice/receiptPosition");
            assertFalse(position.path("available").asBoolean()); assertEquals("UNAVAILABLE", position.path("status").asText());
            assertEquals("INSUFFICIENT_PERMISSION", position.path("unavailableReason").asText());
            assertTrue(position.path("receivedAmount").isNull()); assertTrue(position.path("outstandingAmount").isNull());
            assertFalse(position.path("calculatedAt").asText().isBlank());
        }
        var permission = permissions.findByModuleAndAction(AccessModule.RECEIVABLES, AccessAction.READ).orElseThrow();
        rolePermissions.save(RolePermission.create(f.roleId(), permission.getId()));
        assertEquals("HISTORY_REQUIRES_RECONCILIATION", call(f, get(BASE + "/" + f.invoiceId()), 200)
                .at("/invoice/receiptPosition/unavailableReason").asText());
        assertEquals(before, snapshot(f));
        // A fully received title sharing the contract still cannot prove payment of this invoice.
        var allocation = allocation(f, false);
        UUID titleId = UUID.randomUUID();
        jdbc.update("insert into receivable(id,company_id,contract_id,billing_installment_id,financial_group_id,due_on,original_amount,status) values (?,?,?,?,?,?,?,?)",
                titleId, f.companyId(), f.contractId(), allocation.getBillingInstallmentId(), UUID.randomUUID(), LocalDate.of(2026, 11, 25), new BigDecimal("100.00"), "RECEIVED");
        jdbc.update("insert into receivable_settlement(id,company_id,contract_id,receivable_id,received_on,amount,entry_type,payment_method,status) values (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), f.companyId(), f.contractId(), titleId, LocalDate.of(2026, 10, 27), new BigDecimal("100.00"), "SETTLEMENT", "PIX", "CONFIRMED");
        var state = snapshot(f);
        var position = call(f, get(BASE + "/" + f.invoiceId()), 200).at("/invoice/receiptPosition");
        assertEquals("HISTORY_REQUIRES_RECONCILIATION", position.path("unavailableReason").asText());
        assertTrue(position.path("receivedAmount").isNull());
        assertEquals(state, snapshot(f));
        invoices.updateStatus(invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().cancel());
        assertEquals("INVOICE_CANCELLED", call(f, get(BASE + "/" + f.invoiceId()), 200).at("/invoice/receiptPosition/unavailableReason").asText());
    }

    @Test void indexesOnlyContractsRepresentedByOfficialInvoicesOnceIncludingCancelledHistory() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        invoice(f, "NF-duplicada", null, "2026-10-26");
        var cancelledOnly = extraContract(f, "Histórico", null);
        var cancelled = invoices.save(Invoice.register(f.companyId(), cancelledOnly.getId(), "NF-antiga", null, null,
                LocalDate.of(2026, 10, 20), new BigDecimal("10.00")));
        invoices.updateStatus(cancelled.cancel());
        var empty = extraContract(f, "Sem nota", "Sem-NF");
        var before = snapshot(f);
        var result = call(f, get(BASE + "/contracts"), 200);
        assertEquals(2, result.path("totalItems").asInt()); assertEquals(20, result.path("size").asInt());
        for (JsonNode row : result.path("items")) {
            assertEquals(4, row.size());
            for (String field : List.of("contractId", "contractName", "contractReference", "contractIdentifier")) assertTrue(row.has(field));
            for (String field : List.of("currency", "totalAmount", "receivedAmount", "invoiceCount", "receiptPosition", "accessKey", "storageKey", "work", "customer")) assertFalse(row.has(field));
        }
        assertEquals("Histórico", call(f, get(BASE + "/contracts/" + cancelledOnly.getId()), 200).path("contractName").asText());
        assertEquals(cancelledOnly.getId().toString(), call(f, get(BASE + "/contracts").param("sort", "contractReference,asc"), 200).at("/items/1/contractId").asText());
        call(f, get(BASE + "/contracts/" + empty.getId()), 404);
        call(f, get("/api/v1/company/contracts"), 403);
        assertEquals(before, snapshot(f));
    }

    @Test void paginatesLiteralContractSearchBeforeCountAndRestoresExactContextWithEmptyInvoiceFilter() throws Exception {
        var f = fixture(true, CurrencyCode.USD);
        var first = extraContract(f, "AAA%_!", "REF-A");
        var second = extraContract(f, "AAA%_!", "REF-B");
        for (var contract : List.of(first, second)) invoices.save(Invoice.register(f.companyId(), contract.getId(),
                "NF-grupo-" + contract.getId(), null, null, LocalDate.of(2026, 10, 25), new BigDecimal("10.00")));
        var all = call(f, get(BASE + "/contracts"), 200);
        assertEquals(3, all.path("totalItems").asInt());
        for (int page = 0; page < 3; page++) {
            var result = call(f, get(BASE + "/contracts").param("size", "1").param("page", "" + page), 200);
            assertEquals(3, result.path("totalItems").asInt()); assertEquals(3, result.path("totalPages").asInt());
            assertEquals(all.path("items").get(page), result.at("/items/0"));
        }
        assertTrue(all.at("/items/0/contractId").asText().compareTo(all.at("/items/1/contractId").asText()) < 0);
        for (String search : List.of("%", "_", "!", "aaa", "0001/2027")) {
            int expected = search.equals("0001/2027") ? 3 : 2;
            var result = call(f, get(BASE + "/contracts").param("search", search).param("size", "1"), 200);
            assertEquals(expected, result.path("totalItems").asInt());
        }
        assertEquals(1, call(f, get(BASE + "/contracts").param("search", "ref-b"), 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE + "/contracts").param("search", "12345678000195"), 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(BASE + "/contracts").param("search", "' OR 1=1 --"), 200).path("totalItems").asInt());
        assertEquals(3, call(f, get(BASE + "/contracts").param("search", " "), 200).path("totalItems").asInt());
        for (String sort : List.of("contractName,desc", "contractReference,asc", "contractIdentifier,desc")) {
            assertEquals(3, call(f, get(BASE + "/contracts").param("sort", sort), 200).path("totalItems").asInt());
        }
        assertEquals("AAA%_!", call(f, get(BASE + "/contracts/" + first.getId()), 200).path("contractName").asText());
        assertEquals(0, call(f, get(BASE).param("contractId", first.getId().toString()).param("number", "inexistente"), 200).path("totalItems").asInt());
        assertEquals(first.getId().toString(), call(f, get(BASE).param("contractId", first.getId().toString()), 200).at("/items/0/contractId").asText());
        assertEquals(1, call(f, get(BASE).param("contractId", first.getId().toString()), 200).path("totalItems").asInt());
    }

    @Test void isolatesInvoiceContractIndexAndExactLookupAndRejectsInvalidParameters() throws Exception {
        var f = fixture(true, CurrencyCode.BRL);
        var foreign = fixture(true, CurrencyCode.EUR);
        assertEquals(1, call(f, get(BASE + "/contracts").param("companyId", foreign.companyId().toString()), 200).path("totalItems").asInt());
        call(f, get(BASE + "/contracts/" + foreign.contractId()), 404);
        call(f, get(BASE + "/contracts/" + UUID.randomUUID()), 404);
        call(f, get(BASE + "/contracts/invalid"), 400);
        assertEquals(0, call(f, get(BASE).param("contractId", foreign.contractId().toString()), 200).path("totalItems").asInt());
        for (String[] invalid : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"},
                {"sort", "totalAmount,asc"}, {"sort", "contractId,asc"}, {"sort", "contractName,invalid"}, {"sort", "contractName"}, {"search", "x".repeat(201)}}) {
            call(f, get(BASE + "/contracts").param(invalid[0], invalid[1]), 400);
        }
        for (String path : List.of(BASE + "/contracts", BASE + "/contracts/" + f.contractId())) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
            var denied = fixture(false, CurrencyCode.BRL); grant(denied, AccessAction.CREATE_DIRECT);
            call(denied, get(path), 403);
        }
    }

    private Contract extraContract(Fixture f, String name, String reference) {
        var source = contracts.findByIdAndCompanyId(f.contractId(), f.companyId()).orElseThrow();
        return contracts.save(Contract.create(f.companyId(), source.getWorkId(), "0001/2027", ContractPricingMode.FIXED,
                reference, name, ContractStatus.ACTIVE, null, null, null, source.getCurrency()));
    }

    private List<Map<String, Object>> snapshot(Fixture f) {
        return jdbc.queryForList("""
                select 'invoice' kind,id::text,data::text from (select id,to_jsonb(i) data from invoice i where company_id=?) a
                union all select 'item',id::text,to_jsonb(i)::text from invoice_item i where company_id=?
                union all select 'allocation',id::text,to_jsonb(a)::text from billing_allocation a where company_id=?
                union all select 'receivable',id::text,to_jsonb(r)::text from receivable r where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=?
                union all select 'audit',id::text,to_jsonb(a)::text from audit_event a where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }

    private BillingAllocation allocation(Fixture f, boolean discount) {
        var installment = installments.save(BillingInstallment.createApproved(f.companyId(), f.contractId(), "PARC%_!", null,
                LocalDate.of(2026, 10, 25), LocalDate.of(2026, 11, 25)));
        if (discount) return allocations.save(BillingAllocation.createContractDiscount(f.companyId(), installment.getId(), f.contractId(), new BigDecimal("-0.10")).activate());
        var service = services.save(ContractService.create(f.companyId(), f.contractId(), "Montagem", null,
                ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal("100.00")));
        return allocations.save(BillingAllocation.createService(f.companyId(), installment.getId(), f.contractId(), service.getId(), new BigDecimal("100.00")).activate());
    }

    private Invoice invoice(Fixture f, String number, String series, String date) {
        return invoices.save(Invoice.register(f.companyId(), f.contractId(), number, series, "private-fiscal-key-" + UUID.randomUUID(), LocalDate.parse(date), new BigDecimal("99.90")));
    }

    private JsonNode call(Fixture f, MockHttpServletRequestBuilder request, int expected) throws Exception {
        var result = mvc.perform(request.with(jwt().jwt(token -> token.subject(f.userId().toString())))).andExpect(status().is(expected));
        if (expected >= 400) result.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private Fixture fixture(boolean read, CurrencyCode currency) {
        String suffix = UUID.randomUUID().toString();
        var company = companies.save(Company.create("Emitente", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Cliente", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Obra", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "0001/2027", ContractPricingMode.FIXED, "REF-C", "Contrato usina", ContractStatus.ACTIVE, null, null, null, currency));
        User user = User.create(suffix + "@example.test", UserType.COMPANY); user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), company.getId()));
        var role = roles.save(Role.create(company.getId(), "Operador", null)); userRoles.save(UserRole.create(user.getId(), role.getId(), company.getId()));
        var f = new Fixture(company.getId(), user.getId(), role.getId(), contract.getId(), null);
        Invoice invoice = invoice(f, "NF-1", null, "2026-10-25");
        f = new Fixture(f.companyId(), f.userId(), f.roleId(), f.contractId(), invoice.getId());
        if (read) grant(f, AccessAction.READ);
        return f;
    }

    private void grant(Fixture f, AccessAction action) {
        var permission = permissions.findByModuleAndAction(AccessModule.INVOICES, action).orElseThrow();
        rolePermissions.save(RolePermission.create(f.roleId(), permission.getId()));
    }
    private record Fixture(UUID companyId, UUID userId, UUID roleId, UUID contractId, UUID invoiceId) { }
}
