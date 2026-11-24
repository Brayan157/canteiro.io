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
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
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
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceItem;
import com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableCredit;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementEntryType;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementStatus;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.receivables.domain.TaxObligation;
import com.renovar.canteiro.io.receivables.domain.TaxObligationRepository;
import com.renovar.canteiro.io.receivables.domain.TaxTreatment;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
@Import(BillingReceiptsAndSettlementDateApiIntegrationTest.UtcBoundaryConfiguration.class)
class BillingReceiptsAndSettlementDateApiIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String BILLING = "/api/v1/company/billing-control/contracts";
    private static final String TITLES = "/api/v1/company/receivables/";
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private ContractServiceRepository services;
    @Autowired private BillingInstallmentRepository installments;
    @Autowired private BillingAllocationRepository allocations;
    @Autowired private ReceivableRepository titles;
    @Autowired private ReceivableSettlementRepository settlements;
    @Autowired private ReceivableOriginRepository origins;
    @Autowired private InvoiceRepository invoices;
    @Autowired private InvoiceItemRepository invoiceItems;
    @Autowired private TaxObligationRepository taxes;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private ChangeRequestRepository requests;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    private final ObjectMapper json = new ObjectMapper();

    @TestConfiguration
    static class UtcBoundaryConfiguration {
        @Bean @Primary Clock receiptTestClock() {
            // It is still October 25 in Sao Paulo; the official receipt date is October 26 UTC.
            return Clock.fixed(Instant.parse("2026-10-26T00:15:00Z"), ZoneId.of("America/Sao_Paulo"));
        }
    }

    @Test void rejectsTomorrowForDirectAndApprovalOnlyUsersWithoutChangingOfficialOrProposedData() throws Exception {
        for (AccessAction mode : List.of(AccessAction.CREATE_DIRECT, AccessAction.REQUEST_CREATE)) {
            var f = fixture(CurrencyCode.BRL, mode, AccessAction.READ);
            var before = snapshot(f);
            var error = call(f.userId(), post(receiptPath(f)), payment("2026-10-27", "10.00"), 400);
            assertEquals("VALIDATION_FAILED", error.path("code").asText());
            assertEquals("receivedOn", error.at("/violations/0/field").asText());
            assertEquals(before, snapshot(f));
        }
    }

    @Test void publishesUtcDateLimitAndTemporarilyBlocksTodayAndPastReceiptsWithOrWithoutAnInvoice() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.CREATE_DIRECT);
        var before = snapshot(f);
        assertBlocked(call(f.userId(), post(receiptPath(f)), payment("2026-10-26", "10.00"), 409));
        assertEquals(before, snapshot(f));
        Invoice invoice = invoices.save(Invoice.register(f.companyId(), f.contractId(), "NF-present", null, null,
                LocalDate.of(2026, 10, 25), new BigDecimal("100.00")));
        var title = titles.findByIdAndCompanyId(f.titleId(), f.companyId()).orElseThrow();
        var allocation = allocations.findByBillingInstallmentIdAndCompanyId(title.getBillingInstallmentId(), f.companyId()).getFirst();
        invoiceItems.save(InvoiceItem.create(f.companyId(), invoice.getId(), f.contractId(), allocation.getId(), allocation.getContractServiceId(), null, new BigDecimal("100.00")));
        var withInvoice = snapshot(f);
        assertBlocked(call(f.userId(), post(receiptPath(f)), payment("2026-10-25", "5.00"), 409));
        assertEquals(withInvoice, snapshot(f));
        var balance = call(f.userId(), get(TITLES + f.titleId() + "/balance"), null, 200);
        assertEquals("2026-10-26", balance.path("maxReceivedOn").asText());
        assertEquals("0.00", balance.path("confirmedSettlementAmount").asText());
        assertFalse(balance.path("settlementCreationAvailable").asBoolean());
        assertEquals("SETTLEMENT_INVOICE_LINK_REQUIRED", balance.path("settlementCreationBlockReason").asText());
    }

    @Test void rejectsLegacyFutureProposalOnApprovalAndRollsBackTheDecisionAndAudit() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.REQUEST_CREATE);
        var proposal = requests.save(ChangeRequest.create(f.companyId(), f.userId(), AuditModule.RECEIVABLES,
                ChangeRequestOperation.CREATE, "ReceivableSettlement", f.titleId(), 0,
                new ChangeRequestSnapshot(null, new AuditPayload(Map.of("receivableId", f.titleId().toString(),
                        "receivedOn", "2026-10-27", "amount", "10.00", "paymentMethod", "PIX"))), null));
        UUID approver = actor(f.companyId(), AccessAction.APPROVE);
        var before = snapshot(f);
        var error = call(approver, post("/api/v1/company/change-requests/" + proposal.getId() + "/approve"), Map.of(), 400);
        assertEquals("receivedOn", error.at("/violations/0/field").asText());
        assertEquals(ChangeRequestStatus.PENDING, requests.findByIdAndCompanyId(proposal.getId(), f.companyId()).orElseThrow().getStatus());
        assertEquals(before, snapshot(f));
    }

    @Test void blocksApprovalOnlyCreationAndExistingValidProposalsWithoutChangingCashOrDecision() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.REQUEST_CREATE);
        var before = call(f.userId(), get(TITLES + f.titleId() + "/balance"), null, 200);
        var original = snapshot(f);
        assertBlocked(call(f.userId(), post(receiptPath(f)), payment("2026-10-26", "10.00"), 409));
        assertEquals(original, snapshot(f));
        assertEquals(before, call(f.userId(), get(TITLES + f.titleId() + "/balance"), null, 200));
        var pending = requests.save(ChangeRequest.create(f.companyId(), f.userId(), AuditModule.RECEIVABLES,
                ChangeRequestOperation.CREATE, "ReceivableSettlement", f.titleId(), 0,
                new ChangeRequestSnapshot(null, new AuditPayload(Map.of("receivableId", f.titleId().toString(),
                        "receivedOn", "2026-10-26", "amount", "10.00", "paymentMethod", "PIX"))), null));
        grantBilling(f.userId(), f.companyId());
        assertMoney(f, "0.00");
        UUID approver = actor(f.companyId(), AccessAction.APPROVE);
        var pendingSnapshot = snapshot(f);
        assertBlocked(call(approver, post("/api/v1/company/change-requests/" + pending.getId() + "/approve"), Map.of(), 409));
        assertEquals(ChangeRequestStatus.PENDING, requests.findByIdAndCompanyId(pending.getId(), f.companyId()).orElseThrow().getStatus());
        assertEquals(pendingSnapshot, snapshot(f)); assertMoney(f, "0.00");
    }

    @Test void returnsSignedCashExcludingRetentionsCreditsInvoiceAmountsAndReversedReceipts() throws Exception {
        var f = fixture(CurrencyCode.USD, AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.CANCEL_DIRECT);
        grantBilling(f.userId(), f.companyId());
        var first = save(f.companyId(), f.contractId(), f.titleId(), "25.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        var root = titles.findByIdAndCompanyId(f.titleId(), f.companyId()).orElseThrow();
        var allocation = allocations.findByBillingInstallmentIdAndCompanyId(root.getBillingInstallmentId(), f.companyId()).getFirst();
        jdbc.update("insert into receivable_settlement_allocation(company_id,settlement_id,billing_allocation_id,amount) values (?,?,?,?)",
                f.companyId(), first.getId(), allocation.getId(), new BigDecimal("25.00"));
        call(f.userId(), post(receiptPath(f) + "/" + first.getId() + "/reversal"), Map.of("reason", "Synthetic correction"), 201);
        save(f.companyId(), f.contractId(), f.titleId(), "40.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        Invoice invoice = invoices.save(Invoice.register(f.companyId(), f.contractId(), "NF-test", null, null,
                LocalDate.of(2026, 10, 25), new BigDecimal("100.00")));
        taxes.save(TaxObligation.register(f.companyId(), invoice.getId(), f.contractId(), f.titleId(), "Synthetic retention", null,
                null, new BigDecimal("10.00"), TaxTreatment.WITHHELD_BY_CUSTOMER));
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> origins.saveCredit(
                new ReceivableCredit(null, f.companyId(), f.titleId(), f.contractId(), new BigDecimal("5.00"),
                        "Synthetic credit", null, f.userId(), null)));
        var otherTitle = title(f.companyId(), f.contractId(), "SIBLING", "100.00");
        save(f.companyId(), f.contractId(), otherTitle.getId(), "7.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        save(f.companyId(), f.contractId(), otherTitle.getId(), "3.00", ReceivableSettlementStatus.CANCELLED, "2026-10-25");
        var before = snapshot(f);
        assertMoney(f, "47.00");
        assertEquals(before, snapshot(f));
        assertEquals("45.00", call(f.userId(), get(TITLES + f.titleId() + "/balance"), null, 200).path("outstandingAmount").asText());
    }

    @Test void preservesCashOfRenegotiatedParentsWithoutCountingChildrenAsNewCash() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ);
        grantBilling(f.userId(), f.companyId());
        save(f.companyId(), f.contractId(), f.titleId(), "20.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        var original = titles.findByIdAndCompanyId(f.titleId(), f.companyId()).orElseThrow();
        titles.updateStatus(original.withStatus(ReceivableStatus.RENEGOTIATED));
        var child = titles.saveRenegotiated(Receivable.create(f.companyId(), f.contractId(), original.getBillingInstallmentId(),
                original.getFinancialGroupId(), "RENEG", null, null, LocalDate.of(2026, 12, 1), new BigDecimal("80.00")), f.titleId());
        assertMoney(f, "20.00");
        save(f.companyId(), f.contractId(), child.getId(), "5.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        assertMoney(f, "25.00");
    }

    @Test void preservesHistoricalFutureEntriesWithoutSilentlyChangingOrFilteringThem() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        grantBilling(f.userId(), f.companyId());
        save(f.companyId(), f.contractId(), f.titleId(), "12.00", ReceivableSettlementStatus.CONFIRMED, "2099-01-01");
        var before = snapshot(f);
        assertMoney(f, "12.00");
        assertEquals(before, snapshot(f));
    }

    @Test void requiresBillingReadAndHidesCashWithoutReceivablesReadRatherThanReturningZero() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        call(f.userId(), get(BILLING), null, 403);
        call(f.userId(), get(BILLING + "/" + f.contractId()), null, 403);
        UUID billingOnly = actor(f.companyId());
        grantBilling(billingOnly, f.companyId());
        save(f.companyId(), f.contractId(), f.titleId(), "25.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        for (JsonNode projection : List.of(call(billingOnly, get(BILLING), null, 200).at("/items/0"),
                call(billingOnly, get(BILLING + "/" + f.contractId()), null, 200))) {
            assertTrue(projection.has("receivedAmount")); assertTrue(projection.path("receivedAmount").isNull());
            assertFalse(projection.path("receivedAmountAvailable").asBoolean());
        }
        mvc.perform(get(BILLING)).andExpect(status().isUnauthorized());
    }

    @Test void isolatesTenantContractFiltersPaginationAndBatchQueriesAndPreservesCanonicalLargeAmounts() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ);
        var other = fixture(CurrencyCode.BRL, AccessAction.READ);
        grantBilling(f.userId(), f.companyId()); grantBilling(other.userId(), other.companyId());
        save(f.companyId(), f.contractId(), f.titleId(), "9007199254740993.01", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        save(other.companyId(), other.contractId(), other.titleId(), "65.00", ReceivableSettlementStatus.CONFIRMED, "2026-10-25");
        assertMoney(f, "9007199254740993.01"); assertMoney(other, "65.00");
        call(f.userId(), get(BILLING + "/" + other.contractId()), null, 404);
        var page = call(f.userId(), get(BILLING).param("companyId", other.companyId().toString()).param("size", "1"), null, 200);
        assertEquals(1, page.path("totalItems").asInt()); assertEquals(f.contractId().toString(), page.at("/items/0/contractId").asText());
        assertEquals(0, call(f.userId(), get(BILLING).param("contractId", other.contractId().toString()), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f.userId(), get(BILLING).param("page", "1").param("size", "1"), null, 200).path("items").size());
        var totals = settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(f.contractId(), other.contractId()), f.companyId());
        assertEquals(Map.of(f.contractId(), new BigDecimal("9007199254740993.01")), totals);
        assertEquals(Map.of(), settlements.confirmedAmountsByContractIdsAndCompanyId(List.of(), f.companyId()));
    }

    @Test void returnsOfficialZeroOnlyForAuthorizedEmptyHistory() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        grantBilling(f.userId(), f.companyId()); assertMoney(f, "0.00");
    }

    @Test void containmentPreservesAuthenticationPermissionsAndForeignTitle404() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var other = fixture(CurrencyCode.BRL, AccessAction.CREATE_DIRECT);
        var before = snapshot(f);
        call(f.userId(), post(receiptPath(f)), payment("2026-10-26", "10.00"), 403);
        call(other.userId(), post(receiptPath(f)), payment("2026-10-26", "10.00"), 404);
        mvc.perform(post(receiptPath(f)).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payment("2026-10-26", "10.00"))))
                .andExpect(status().isUnauthorized());
        assertEquals(before, snapshot(f));
    }

    @Test void exportsExecutableCashAndReceiptDateContractWithoutInventingInvoiceEligibility() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        var root = json.readTree(specification);
        for (String schema : List.of("BillingControlSummaryResponse", "ContractBillingControlResponse")) {
            var properties = root.path("components").path("schemas").path(schema).path("properties");
            assertEquals("decimal", properties.at("/receivedAmount/format").asText());
            assertTrue(properties.at("/receivedAmount/type").toString().contains("string"));
            assertTrue(properties.at("/receivedAmount/type").toString().contains("null"));
            assertEquals("boolean", properties.at("/receivedAmountAvailable/type").asText());
        }
        assertEquals("date", root.at("/components/schemas/ReceivableBalanceResponse/properties/maxReceivedOn/format").asText());
        assertEquals("boolean", root.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationAvailable/type").asText());
        assertTrue(root.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationBlockReason").toString().contains("SETTLEMENT_INVOICE_LINK_REQUIRED"));
        var blockReason = root.at("/components/schemas/ReceivableBalanceResponse/properties/settlementCreationBlockReason");
        assertTrue(blockReason.path("type").toString().contains("null"));
        assertFalse(blockReason.has("enum"), "A string/null reason must not be restricted by a non-null-only enum");
        assertEquals("string", root.at("/components/schemas/ProblemDetail/properties/reason/type").asText());
        assertEquals("array", root.at("/components/schemas/ProblemDetail/properties/violations/type").asText());
        assertTrue(root.at("/components/schemas/CreateReceivableSettlementRequest/properties/receivedOn/description").asText().contains("UTC"));
        assertFalse(root.path("paths").has(TITLES + "{receivableId}/settlement-eligibility"));
        String export = System.getenv("RECEIPT_CONTROL_OPENAPI_EXPORT_PATH");
        if (export != null && !export.isBlank()) {
            Path target = Path.of(export).toAbsolutePath().normalize();
            Files.createDirectories(target.getParent()); Files.writeString(target, specification);
        }
    }

    private void assertBlocked(JsonNode error) {
        assertEquals("BUSINESS_RULE_VIOLATION", error.path("code").asText());
        assertEquals("SETTLEMENT_INVOICE_LINK_REQUIRED", error.path("reason").asText());
    }
    private void assertMoney(Fixture f, String expected) throws Exception {
        for (JsonNode projection : List.of(call(f.userId(), get(BILLING), null, 200).at("/items/0"),
                call(f.userId(), get(BILLING + "/" + f.contractId()), null, 200))) {
            assertTrue(projection.path("receivedAmountAvailable").asBoolean()); assertTrue(projection.path("receivedAmount").isString());
            assertEquals(expected, projection.path("receivedAmount").asText()); assertEquals(f.currency().name(), projection.path("currency").asText());
        }
    }
    private Map<String, Object> payment(String date, String amount) { return Map.of("receivedOn", date, "amount", amount, "paymentMethod", "PIX"); }
    private String receiptPath(Fixture f) { return TITLES + f.titleId() + "/settlements"; }
    private JsonNode call(UUID actor, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        request.with(jwt().jwt(token -> token.subject(actor.toString())));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(request).andExpect(status().is(expected));
        if (expected >= 400) response.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(response.andReturn().getResponse().getContentAsString());
    }
    private ReceivableSettlement save(UUID company, UUID contract, UUID title, String amount, ReceivableSettlementStatus status, String date) {
        return settlements.save(ReceivableSettlement.rehydrate(null, company, title, contract, LocalDate.parse(date), new BigDecimal(amount),
                ReceivableSettlementEntryType.SETTLEMENT, null, ReceivableSettlementPaymentMethod.PIX, null, status, null, null));
    }
    private Receivable title(UUID company, UUID contract, String reference, String amount) {
        var installment = installments.save(BillingInstallment.createApproved(company, contract, reference, null, null, LocalDate.of(2026, 11, 1)));
        return titles.save(Receivable.create(company, contract, installment.getId(), UUID.randomUUID(), reference, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1), new BigDecimal(amount)));
    }
    private UUID actor(UUID company, AccessAction... receiptActions) {
        User user = User.create(UUID.randomUUID() + "@example.test", UserType.COMPANY);
        user.activate("synthetic-hash", Instant.parse("2026-10-01T00:00:00Z")); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), company));
        var role = roles.save(Role.create(company, "R-" + UUID.randomUUID(), null));
        userRoles.save(UserRole.create(user.getId(), role.getId(), company));
        for (AccessAction action : receiptActions) rolePermissions.save(RolePermission.create(role.getId(), permissions.findByModuleAndAction(AccessModule.RECEIVABLES, action).orElseThrow().getId()));
        return user.getId();
    }
    private void grantBilling(UUID actor, UUID company) {
        var role = userRoles.findByUserIdAndCompanyId(actor, company).getFirst();
        rolePermissions.save(RolePermission.create(role.getRoleId(), permissions.findByModuleAndAction(AccessModule.BILLING, AccessAction.READ).orElseThrow().getId()));
    }
    private Fixture fixture(CurrencyCode currency, AccessAction... receiptActions) {
        String unique = UUID.randomUUID().toString();
        var company = companies.save(Company.create("Synthetic company", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), unique + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Synthetic customer", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Work", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "CONTRACT", ContractPricingMode.FIXED, null, "Contract", ContractStatus.ACTIVE, null, null, null, currency));
        var title = title(company.getId(), contract.getId(), "PARCEL", "100.00");
        var service = services.save(ContractService.create(company.getId(), contract.getId(), "Service", null, ContractServiceStatus.ACTIVE, BigDecimal.ONE, new BigDecimal("100.00")));
        allocations.save(BillingAllocation.createService(company.getId(), title.getBillingInstallmentId(), contract.getId(), service.getId(), new BigDecimal("100.00")).activate());
        return new Fixture(company.getId(), actor(company.getId(), receiptActions), contract.getId(), title.getId(), currency);
    }
    private List<Map<String, Object>> snapshot(Fixture f) {
        return jdbc.queryForList("""
                select 'title' kind,id::text,to_jsonb(r)::text data from receivable r where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=?
                union all select 'attribution',settlement_id::text||billing_allocation_id::text,to_jsonb(s)::text from receivable_settlement_allocation s where company_id=?
                union all select 'audit',id::text,to_jsonb(a)::text from audit_event a where company_id=?
                union all select 'request',id::text,to_jsonb(c)::text from change_request c where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }
    private record Fixture(UUID companyId, UUID userId, UUID contractId, UUID titleId, CurrencyCode currency) { }
}
