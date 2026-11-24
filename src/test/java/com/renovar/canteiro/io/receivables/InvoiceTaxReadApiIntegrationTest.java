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
import com.renovar.canteiro.io.receivables.application.CalculateReceivableBalanceUseCase;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.Invoice;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
class InvoiceTaxReadApiIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companies;
    @Autowired private FinalCustomerRepository customers;
    @Autowired private WorkRepository works;
    @Autowired private ContractRepository contracts;
    @Autowired private InvoiceRepository invoices;
    @Autowired private TaxObligationRepository taxes;
    @Autowired private BillingInstallmentRepository installments;
    @Autowired private ReceivableRepository receivables;
    @Autowired private CalculateReceivableBalanceUseCase balances;
    @Autowired private UserRepository users;
    @Autowired private CompanyUserRepository memberships;
    @Autowired private PasswordHasher hasher;
    @Autowired private RoleRepository roles;
    @Autowired private PermissionRepository permissions;
    @Autowired private RolePermissionRepository rolePermissions;
    @Autowired private UserRoleRepository userRoles;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private com.renovar.canteiro.io.contracts.domain.ContractServiceRepository services;
    @Autowired private com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository allocations;
    @Autowired private com.renovar.canteiro.io.receivables.domain.InvoiceItemRepository invoiceItems;
    @Autowired private com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository settlements;
    @Autowired private org.springframework.transaction.PlatformTransactionManager transactions;
    private final ObjectMapper json = new ObjectMapper();

    @Test void cancellationPreservesBillingDebtAndHistoryAndRequiresPermissionAndTenant() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var other = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var reader = taxActor(f, AccessAction.READ);
        var before = snapshot(f);
        call(reader, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 403);
        call(other, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 404);
        assertEquals(before, snapshot(f));
        call(f, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 200);
        assertEquals("CANCELLED", invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().getStatus().name());
        assertEquals("OPEN", receivables.findByIdAndCompanyId(f.receivableId(), f.companyId()).orElseThrow().getStatus().name());
        assertEquals(new BigDecimal("100.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
        assertEquals(1, jdbc.queryForObject("select count(*) from billing_allocation where company_id=? and status='ACTIVE'", Integer.class, f.companyId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from billing_installment where company_id=? and status='OPEN'", Integer.class, f.companyId()));
        assertEquals(1, jdbc.queryForObject("select count(*) from invoice_item where company_id=? and status='CANCELLED'", Integer.class, f.companyId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from receivable_settlement where company_id=?", Integer.class, f.companyId()));
    }

    @Test void cancellationBlocksPartialAndFullHistoricalReceiptsBeforeDirectOrProposedChanges() throws Exception {
        for (String amount : List.of("20.00", "100.00")) {
            var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
            var requester = taxActor(f, AccessAction.REQUEST_CANCEL);
            historicalReceipt(f, amount);
            var before = snapshot(f);
            for (var actor : List.of(f, requester)) {
                var problem = call(actor, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 409);
                assertEquals("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", problem.path("reason").asText());
                assertEquals(before, snapshot(f));
            }
        }
    }

    @Test void sharedInstallmentReceiptBlocksEachInvoiceWithoutInventingAttribution() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var originalItem = invoiceItems.findByInvoiceIdAndCompanyId(f.invoiceId(), f.companyId()).getFirst();
        // Synthetic historical split: two notes cover different portions of the same allocation/title.
        jdbc.update("update invoice set total_amount=50 where id=?", f.invoiceId());
        jdbc.update("update invoice_item set allocated_amount=50 where id=?", originalItem.getId());
        var second = invoices.save(Invoice.register(f.companyId(), f.contractId(), "NF-TRIB-2", null, null,
                LocalDate.of(2026, 10, 26), new BigDecimal("50.00")));
        invoiceItems.save(com.renovar.canteiro.io.receivables.domain.InvoiceItem.create(f.companyId(), second.getId(), f.contractId(),
                originalItem.getBillingAllocationId(), originalItem.getContractServiceId(), "Serviço", new BigDecimal("50.00")));
        historicalReceipt(f, "20.00");
        var before = snapshot(f);
        for (UUID id : List.of(f.invoiceId(), second.getId())) {
            var problem = call(f, post("/api/v1/company/invoices/" + id + "/cancellation"), Map.of("reason", "Correção"), 409);
            assertEquals("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", problem.path("reason").asText());
        }
        assertEquals(before, snapshot(f));
    }

    @Test void cancellationProposalPreservesOfficialStateAndApprovalRevalidatesReceipts() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var requester = taxActor(f, AccessAction.REQUEST_CANCEL);
        var approver = taxActor(f, AccessAction.APPROVE);
        var beforeProposal = officialSnapshot(f);
        var pending = call(requester, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 202);
        assertEquals(beforeProposal, officialSnapshot(f));
        String requestId = pending.path("changeRequestId").asText();
        assertFalse(requestId.isBlank());
        assertEquals("REGISTERED", invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().getStatus().name());
        assertEquals(1, jdbc.queryForObject("select count(*) from invoice_item where company_id=? and status='REGISTERED'", Integer.class, f.companyId()));
        assertEquals(new BigDecimal("100.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
        historicalReceipt(f, "20.00");
        var before = snapshot(f);
        var problem = call(approver, post("/api/v1/company/change-requests/" + requestId + "/approve"), Map.of(), 409);
        assertEquals("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", problem.path("reason").asText());
        assertEquals(before, snapshot(f));
    }

    @Test void cancellationApprovalAppliesOnlyAfterAnIndependentApprover() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        var requester = taxActor(f, AccessAction.REQUEST_CANCEL);
        var approver = taxActor(f, AccessAction.APPROVE);
        var pending = call(requester, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 202);
        call(approver, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 200);
        assertEquals("CANCELLED", invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().getStatus().name());
        assertEquals(new BigDecimal("100.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
    }

    @Test void cancellationRequiresReviewForIncompleteOriginsOrUnexplainedPaidStatus() throws Exception {
        var missing = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        jdbc.update("update invoice_item set status='CANCELLED' where invoice_id=?", missing.invoiceId());
        var unexplained = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        jdbc.update("update receivable set status='RECEIVED' where id=?", unexplained.receivableId());
        for (var f : List.of(missing, unexplained)) {
            var before = snapshot(f);
            var problem = call(f, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 409);
            assertEquals("INVOICE_CANCELLATION_REQUIRES_REVIEW", problem.path("reason").asText());
            assertEquals(before, snapshot(f));
        }
    }

    @Test void cancellationAllowsAnExactlyReversedHistoricalReceiptWithoutCreatingAnotherReversal() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var receipt = historicalReceipt(f, "20.00");
        settlements.save(com.renovar.canteiro.io.receivables.domain.ReceivableSettlement.reverse(receipt, LocalDate.of(2026, 10, 27), "Estorno sintético prévio"));
        call(f, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 200);
        assertEquals(2, settlements.findByReceivableIdAndCompanyId(f.receivableId(), f.companyId()).size());
        assertEquals(new BigDecimal("100.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
    }

    @Test void cancellationBlocksTaxPaymentsBeforeAnyDirectOrProposedEffect() throws Exception {
        for (String taxStatus : List.of("PAID", "PARTIALLY_PAID")) {
            var f = fixture(CurrencyCode.BRL, AccessAction.CREATE_DIRECT, AccessAction.CANCEL_DIRECT);
            register(f, "Tributo sintético", TaxTreatment.PAYABLE_BY_COMPANY, "10.00", 201);
            jdbc.update("update payable set status=? where company_id=?", taxStatus, f.companyId());
            var requester = taxActor(f, AccessAction.REQUEST_CANCEL);
            var before = snapshot(f);
            for (var actor : List.of(f, requester)) {
                var problem = call(actor, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 409);
                assertEquals("INVOICE_CANCELLATION_HAS_TAX_PAYMENTS", problem.path("reason").asText());
                assertEquals(before, snapshot(f));
            }
        }
    }

    @Test void cancellationApprovalAlsoRechecksTaxPaymentsAtomically() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CREATE_DIRECT);
        register(f, "Tributo sintético", TaxTreatment.PAYABLE_BY_COMPANY, "10.00", 201);
        var requester = taxActor(f, AccessAction.REQUEST_CANCEL);
        var approver = taxActor(f, AccessAction.APPROVE);
        var beforeProposal = officialSnapshot(f);
        var pending = call(requester, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 202);
        assertEquals(beforeProposal, officialSnapshot(f));
        jdbc.update("update payable set status='PAID' where company_id=?", f.companyId());
        var before = snapshot(f);
        var problem = call(approver, post("/api/v1/company/change-requests/" + pending.path("changeRequestId").asText() + "/approve"), Map.of(), 409);
        assertEquals("INVOICE_CANCELLATION_HAS_TAX_PAYMENTS", problem.path("reason").asText());
        assertEquals(before, snapshot(f));
    }

    @Test void cancellationRequiresReviewForAnUnbalancedReversalLinkEvenWhenTheCashSumIsZero() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var first = historicalReceipt(f, "20.00");
        historicalReceipt(f, "20.00");
        var reversal = settlements.save(com.renovar.canteiro.io.receivables.domain.ReceivableSettlement.reverse(first,
                LocalDate.of(2026, 10, 27), "Histórico sintético inconsistente"));
        jdbc.update("update receivable_settlement set amount=-40 where id=?", reversal.getId());
        var before = snapshot(f);
        var problem = call(f, post(cancellationPath(f)), Map.of("reason", "Correção documental"), 409);
        assertEquals("INVOICE_CANCELLATION_REQUIRES_REVIEW", problem.path("reason").asText());
        assertEquals(before, snapshot(f));
    }

    @Test void cancellationSerializesWithReceiptAndRejectsStaleVersionOrActiveCash() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.CANCEL_DIRECT);
        var beforeInvoice = invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow();
        try (var pool = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var started = new java.util.concurrent.CountDownLatch(1);
            var future = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<JsonNode>>();
            new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(tx -> {
                receivables.findByIdAndCompanyIdWithSettlementLock(f.receivableId(), f.companyId()).orElseThrow();
                future.set(pool.submit(() -> {
                    started.countDown();
                    return call(f, post(cancellationPath(f)), Map.of("reason", "Correção concorrente"), 409);
                }));
                try {
                    assertTrue(started.await(5, java.util.concurrent.TimeUnit.SECONDS));
                    assertThrows(java.util.concurrent.TimeoutException.class, () -> future.get().get(300, java.util.concurrent.TimeUnit.MILLISECONDS));
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                historicalReceipt(f, "20.00");
            });
            var problem = future.get().get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals("BUSINESS_RULE_VIOLATION", problem.path("code").asText(), problem.toString());
            if (!problem.path("reason").isMissingNode()) {
                assertEquals("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", problem.path("reason").asText(), problem.toString());
            } else {
                // A stale entity loaded before the lock may be rejected by Hibernate's version check first.
                assertEquals("The contract billing balance changed concurrently. Retry the operation.", problem.path("detail").asText(), problem.toString());
            }
        }
        assertEquals(beforeInvoice.getStatus(), invoices.findByIdAndCompanyId(f.invoiceId(), f.companyId()).orElseThrow().getStatus());
        assertEquals(new BigDecimal("20.00"), balances.calculate(f.companyId(), f.receivableId()).confirmedSettlementAmount());
        assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where company_id=?", Integer.class, f.companyId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from change_request where company_id=?", Integer.class, f.companyId()));
        var problem = call(f, post(cancellationPath(f)), Map.of("reason", "Nova consulta após conflito"), 409);
        assertEquals("INVOICE_CANCELLATION_HAS_ACTIVE_RECEIPTS", problem.path("reason").asText(), problem.toString());
    }

    private String cancellationPath(Fixture f) { return "/api/v1/company/invoices/" + f.invoiceId() + "/cancellation"; }

    private List<Map<String, Object>> officialSnapshot(Fixture f) {
        var rows = new java.util.ArrayList<>(snapshot(f).stream()
                .filter(row -> !List.of("audit", "request").contains(row.get("kind"))).toList());
        rows.addAll(jdbc.queryForList("select 'contract' kind,id::text,to_jsonb(c)::text data from contract c where company_id=?", f.companyId()));
        return rows;
    }

    private com.renovar.canteiro.io.receivables.domain.ReceivableSettlement historicalReceipt(Fixture f, String amount) {
        // Synthetic legacy fixture only; the public receipt endpoint remains blocked by F05-32.
        return settlements.save(com.renovar.canteiro.io.receivables.domain.ReceivableSettlement.confirm(f.companyId(), f.receivableId(), f.contractId(),
                LocalDate.of(2026, 10, 26), new BigDecimal(amount),
                com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod.PIX, "Histórico sintético"));
    }

    @Test void readsAllTreatmentsAndCanonicalCurrencyWithoutReapplyingFinancialEffectsOrDisclosingTitles() throws Exception {
        var f = fixture(CurrencyCode.USD, AccessAction.READ, AccessAction.CREATE_DIRECT);
        registerAll(f);
        assertEquals(new BigDecimal("90.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
        assertEquals(1, jdbc.queryForObject("select count(*) from payable where company_id=?", Integer.class, f.companyId()));
        var before = snapshot(f);
        var page = call(f, get(path(f)).param("sort", "treatment,asc"), null, 200);
        assertEquals(3, page.path("totalItems").asInt());
        assertEquals(20, page.path("size").asInt());
        var treatments = new java.util.HashSet<String>();
        for (var item : page.path("items")) {
            treatments.add(item.path("treatment").asText());
            assertEquals("USD", item.path("currency").asText());
            assertEquals("10.00", item.path("amount").asText()); assertTrue(item.path("amount").isString());
            assertEquals("REGISTERED", item.path("status").asText());
            assertEquals(f.invoiceId().toString(), item.path("invoiceId").asText());
            assertEquals(f.contractId().toString(), item.path("contractId").asText());
            if (item.path("treatment").asText().equals("PAYABLE_BY_COMPANY")) assertEquals("2026-11-25", item.path("dueOn").asText());
            else assertTrue(item.path("dueOn").isNull());
            for (String field : List.of("receivableId", "payableId", "receivable", "payable", "balance", "outstandingAmount", "accessKey", "storageKey")) assertFalse(item.has(field));
        }
        assertEquals(java.util.Set.of("PAYABLE_BY_COMPANY", "WITHHELD_BY_CUSTOMER", "INFORMATIONAL"), treatments);
        call(f, get(path(f)).param("treatment", "WITHHELD_BY_CUSTOMER"), null, 200);
        assertEquals(before, snapshot(f));
        assertEquals(new BigDecimal("90.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
        grant(f, AccessModule.RECEIVABLES, AccessAction.READ); grant(f, AccessModule.PAYABLES, AccessAction.READ);
        assertEquals(page, call(f, get(path(f)).param("sort", "treatment,asc"), null, 200));
    }

    @Test void cancelledInvoiceKeepsAllOfficialCancelledTaxesAmountsAndNullableFields() throws Exception {
        var f = fixture(CurrencyCode.EUR, AccessAction.READ, AccessAction.CREATE_DIRECT, AccessAction.CANCEL_DIRECT);
        registerAll(f);
        call(f, post("/api/v1/company/invoices/" + f.invoiceId() + "/cancellation"), Map.of("reason", "Cancelamento sintético para histórico"), 200);
        var before = snapshot(f);
        var all = call(f, get(path(f)), null, 200);
        assertEquals(3, all.path("totalItems").asInt());
        for (var tax : all.path("items")) {
            assertEquals("CANCELLED", tax.path("status").asText()); assertEquals("10.00", tax.path("amount").asText());
            assertEquals("EUR", tax.path("currency").asText()); assertTrue(tax.path("description").isNull());
        }
        assertEquals(0, call(f, get(path(f)).param("status", "REGISTERED"), null, 200).path("totalItems").asInt());
        assertEquals(3, call(f, get(path(f)).param("status", "CANCELLED"), null, 200).path("totalItems").asInt());
        assertEquals(before, snapshot(f));
    }

    @Test void isolatesTwoCompaniesAndTheInvoiceContractAndReceivableChain() throws Exception {
        var owner = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.CREATE_DIRECT);
        var other = fixture(CurrencyCode.BRL, AccessAction.READ);
        register(owner, "Tributo do dono", TaxTreatment.INFORMATIONAL, "1.23", 201);
        call(other, get(path(owner)), null, 404);
        call(other, get("/api/v1/company/invoices/" + UUID.randomUUID() + "/tax-obligations"), null, 404);
        assertEquals(0, call(other, get(path(other)).param("companyId", owner.companyId().toString()), null, 200).path("totalItems").asInt());
        var sibling = invoices.save(Invoice.register(owner.companyId(), owner.contractId(), "NF-OUTRA", null, null, LocalDate.of(2026, 10, 26), new BigDecimal("100.00")));
        assertEquals(0, call(owner, get("/api/v1/company/invoices/" + sibling.getId() + "/tax-obligations"), null, 200).path("totalItems").asInt());
        assertThrows(DataIntegrityViolationException.class, () -> taxes.save(TaxObligation.register(owner.companyId(), owner.invoiceId(), other.contractId(), null,
                "Cadeia inválida", null, null, new BigDecimal("1.00"), TaxTreatment.INFORMATIONAL)));
        assertThrows(DataIntegrityViolationException.class, () -> taxes.save(TaxObligation.register(owner.companyId(), owner.invoiceId(), owner.contractId(), other.receivableId(),
                "Título estrangeiro", null, null, new BigDecimal("1.00"), TaxTreatment.WITHHELD_BY_CUSTOMER)));
        var secondContract = contracts.save(Contract.create(owner.companyId(), owner.workId(), null, "Outro contrato", ContractStatus.ACTIVE, null, null, null));
        var secondReceivable = title(owner.companyId(), secondContract.getId());
        assertThrows(DataIntegrityViolationException.class, () -> taxes.save(TaxObligation.register(owner.companyId(), owner.invoiceId(), owner.contractId(), secondReceivable.getId(),
                "Título de outro contrato", null, null, new BigDecimal("1.00"), TaxTreatment.WITHHELD_BY_CUSTOMER)));
        assertEquals(1, call(owner, get(path(owner)), null, 200).path("totalItems").asInt());
    }

    @Test void paginatesOrdersTiesAndCombinesOnlyExplicitStatusTreatmentFilters() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.CREATE_DIRECT);
        var first = register(f, "Mesmo nome", TaxTreatment.INFORMATIONAL, "3.01", 201);
        register(f, "Mesmo nome", TaxTreatment.INFORMATIONAL, "1.99", 201);
        register(f, "Mesmo nome", TaxTreatment.INFORMATIONAL, "99.90", 201);
        UUID firstId = UUID.fromString(first.at("/taxObligation/id").asText());
        taxes.updateStatus(taxes.findByIdAndCompanyId(firstId, f.companyId()).orElseThrow().cancel());
        var all = call(f, get(path(f)).param("sort", "name,asc"), null, 200);
        assertEquals(3, all.path("totalItems").asInt());
        assertTrue(all.at("/items/0/id").asText().compareTo(all.at("/items/1/id").asText()) < 0);
        assertTrue(all.at("/items/1/id").asText().compareTo(all.at("/items/2/id").asText()) < 0);
        for (int page = 0; page < 3; page++) {
            var single = call(f, get(path(f)).param("sort", "name,asc").param("page", Integer.toString(page)).param("size", "1"), null, 200);
            assertEquals(all.path("items").get(page), single.at("/items/0")); assertEquals(3, single.path("totalPages").asInt());
        }
        assertEquals(0, call(f, get(path(f)).param("page", "3").param("size", "1"), null, 200).path("items").size());
        assertEquals(2, call(f, get(path(f)).param("status", "REGISTERED"), null, 200).path("totalItems").asInt());
        assertEquals(1, call(f, get(path(f)).param("status", "CANCELLED").param("treatment", "INFORMATIONAL"), null, 200).path("totalItems").asInt());
        assertEquals(0, call(f, get(path(f)).param("status", "CANCELLED").param("treatment", "PAYABLE_BY_COMPANY"), null, 200).path("totalItems").asInt());
        assertEquals(3, call(f, get(path(f)).param("sort", "status,asc", "createdAt,desc"), null, 200).path("totalItems").asInt());
    }

    @Test void rejectsInvalidTypesPagesAndFreeSortsWithCorrelatedProblemDetails() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ);
        assertEquals(0, call(f, get(path(f)), null, 200).path("totalItems").asInt());
        for (String[] invalid : new String[][] {{"page", "-1"}, {"size", "0"}, {"size", "101"}, {"page", "2147483647"},
                {"sort", "amount,desc"}, {"sort", "dueOn,asc"}, {"sort", "name,invalid"}, {"sort", "name"},
                {"status", "PAID"}, {"treatment", "OTHER"}}) call(f, get(path(f)).param(invalid[0], invalid[1]), null, 400);
        call(f, get("/api/v1/company/invoices/invalid/tax-obligations"), null, 400);
    }

    @Test void requiresAuthenticationAndInvoiceReadIndependentlyOfCreateOrOtherFinancialPermissions() throws Exception {
        String path = "/api/v1/company/invoices/" + UUID.randomUUID() + "/tax-obligations";
        mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
        mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-test-tax-token"))
                .andExpect(status().isUnauthorized()).andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        var creator = fixture(CurrencyCode.BRL, AccessAction.CREATE_DIRECT);
        grant(creator, AccessModule.PAYABLES, AccessAction.READ); grant(creator, AccessModule.RECEIVABLES, AccessAction.READ);
        call(creator, get(path(creator)), null, 403);
    }

    @Test void pendingCreationDoesNotBecomeAnOfficialTaxOrProducePayableOrWithholdingOnRead() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.REQUEST_CREATE);
        for (TaxTreatment treatment : TaxTreatment.values()) {
            var result = register(f, "Pendente " + treatment.name(), treatment, "10.00", 202);
            assertFalse(result.path("changeRequestId").asText().isBlank()); assertTrue(result.path("taxObligation").isNull());
        }
        var before = snapshot(f);
        assertEquals(0, call(f, get(path(f)), null, 200).path("totalItems").asInt());
        assertEquals(before, snapshot(f));
        assertEquals(0, jdbc.queryForObject("select count(*) from payable where company_id=?", Integer.class, f.companyId()));
        assertEquals(new BigDecimal("100.00"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
    }

    @Test void calculatedTaxPreviewsWithoutEffectsThenPreservesAllTreatmentsAndIdempotentTrace() throws Exception {
        for (var currency : CurrencyCode.values()) {
            var f = fixture(currency, AccessAction.READ, AccessAction.CREATE_DIRECT);
            var before = snapshot(f);
            var preview = call(f, post(path(f) + "/preview"), percentage(), 200);
            assertEquals("21.23", preview.path("amount").asText()); assertEquals("1000.00", preview.path("baseAmount").asText());
            assertEquals("2.1234", preview.path("ratePercent").asText()); assertEquals(currency.name(), preview.path("currency").asText());
            assertFalse(preview.path("recorded").asBoolean()); assertTrue(preview.path("taxObligationId").isNull()); assertEquals(before, snapshot(f));
            assertEquals(0, jdbc.queryForObject("select count(*) from invoice_tax_operation where company_id=?", Integer.class, f.companyId()));
            for (var treatment : TaxTreatment.values()) {
                var body = calculatedBody(f, treatment, "21.23");
                var created = call(f, post(path(f) + "/calculated"), body, 201);
                String taxId = created.at("/taxObligation/id").asText();
                assertEquals("21.23", created.at("/taxObligation/amount").asText()); assertTrue(created.at("/calculation/recorded").asBoolean());
                assertEquals(created.path("taxObligation"), call(f, post(path(f) + "/calculated"), body, 200).path("taxObligation"));
                var trace = call(f, get(path(f) + "/" + taxId + "/calculation"), null, 200);
                assertEquals("1000.00", trace.path("baseAmount").asText()); assertEquals("2.1234", trace.path("ratePercent").asText());
                var changed = new HashMap<String,Object>(body); changed.put("name", "Outra intenção"); call(f, post(path(f) + "/calculated"), changed, 409);
            }
            assertEquals(3, call(f, get(path(f)), null, 200).path("totalItems").asInt());
            assertEquals(1, jdbc.queryForObject("select count(*) from payable where company_id=?", Integer.class, f.companyId()));
            assertEquals(new BigDecimal("78.77"), balances.calculate(f.companyId(), f.receivableId()).outstandingAmount());
        }
    }

    @Test void calculatedTaxExplicitAmountModePreservesNullBaseAndRate() throws Exception {
        var f=fixture(CurrencyCode.USD,AccessAction.READ,AccessAction.CREATE_DIRECT);
        var before=snapshot(f);var input=Map.of("mode","AMOUNT","amount","12.34");
        var preview=call(f,post(path(f)+"/preview"),input,200);assertEquals("12.34",preview.path("amount").asText());assertTrue(preview.path("baseAmount").isNull());assertEquals(before,snapshot(f));
        var body=new HashMap<String,Object>(calculatedBody(f,TaxTreatment.PAYABLE_BY_COMPANY,"12.34"));body.put("calculation",input);
        var created=call(f,post(path(f)+"/calculated"),body,201);assertTrue(created.at("/calculation/baseAmount").isNull());assertTrue(created.at("/calculation/ratePercent").isNull());
        assertEquals("12.34",created.at("/taxObligation/amount").asText());assertEquals(created.path("taxObligation"),call(f,post(path(f)+"/calculated"),body,200).path("taxObligation"));
    }

    @Test void calculatedTaxRejectsDivergenceMixedInputsPrecisionAndForeignContext() throws Exception {
        var f = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.CREATE_DIRECT); var other = fixture(CurrencyCode.BRL, AccessAction.READ, AccessAction.CREATE_DIRECT);
        call(other, post(path(f)+"/preview"), percentage(), 404);
        call(other, post(path(f)+"/calculated"), calculatedBody(f,TaxTreatment.INFORMATIONAL,"21.23"),404);
        call(f, post(path(f)+"/calculated"), calculatedBody(f,TaxTreatment.INFORMATIONAL,"21.24"),400);
        for (Object rate : List.of("2.12345", "0", "-1", 2.1234)) {
            call(f,post(path(f)+"/preview"),Map.of("mode","PERCENTAGE","baseAmount","1000.00","ratePercent",rate),400);
        }
        call(f,post(path(f)+"/preview"),Map.of("mode","PERCENTAGE","baseAmount","1000.00","ratePercent","2.1234","amount","21.23"),400);
        call(f,post(path(f)+"/preview"),Map.of("mode","AMOUNT","amount","21.23","baseAmount","1000.00"),400);
        var reader=fixture(CurrencyCode.BRL,AccessAction.READ);call(reader,post(path(reader)+"/preview"),percentage(),403);
        mvc.perform(post(path(f)+"/preview").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(percentage()))).andExpect(status().isUnauthorized());
        assertEquals(0,jdbc.queryForObject("select count(*) from tax_obligation where company_id=?",Integer.class,f.companyId()));
        register(f,"Legado",TaxTreatment.INFORMATIONAL,"10.00",201);
        String legacy=call(f,get(path(f)),null,200).at("/items/0/id").asText();
        var trace=call(f,get(path(f)+"/"+legacy+"/calculation"),null,200);assertFalse(trace.path("recorded").asBoolean());assertTrue(trace.path("baseAmount").isNull());assertEquals("AMOUNT",trace.path("mode").asText());
        call(other,get(path(f)+"/"+legacy+"/calculation"),null,404);
    }

    @Test void calculatedTaxApprovalHasNoEffectUntilApplyAndRevalidatesWithholdingCapacity() throws Exception {
        var f=fixture(CurrencyCode.BRL,AccessAction.READ,AccessAction.CREATE_DIRECT);
        var requester=taxActor(f,AccessAction.READ,AccessAction.REQUEST_CREATE);var approver=taxActor(f,AccessAction.APPROVE);
        var body=calculatedBody(f,TaxTreatment.WITHHELD_BY_CUSTOMER,"21.23");
        var pending=call(requester,post(path(f)+"/calculated"),body,202);assertFalse(pending.has("taxObligation"));assertFalse(pending.has("calculation"));assertEquals(2,pending.size());
        assertEquals(pending,call(requester,post(path(f)+"/calculated"),body,202));assertEquals(0,call(f,get(path(f)),null,200).path("totalItems").asInt());
        call(approver,post("/api/v1/company/change-requests/"+pending.path("changeRequestId").asText()+"/approve"),Map.of(),200);
        assertEquals("21.23",call(requester,post(path(f)+"/calculated"),body,200).at("/taxObligation/amount").asText());
        var second=calculatedBody(f,TaxTreatment.WITHHELD_BY_CUSTOMER,"21.23");var p=call(requester,post(path(f)+"/calculated"),second,202);
        register(f,"Retenção posterior",TaxTreatment.WITHHELD_BY_CUSTOMER,"70.00",201);
        call(approver,post("/api/v1/company/change-requests/"+p.path("changeRequestId").asText()+"/approve"),Map.of(),409);
        assertEquals(2,call(f,get(path(f)),null,200).path("totalItems").asInt());
        var creator=fixture(CurrencyCode.EUR,AccessAction.CREATE_DIRECT);
        var direct=calculatedBody(creator,TaxTreatment.INFORMATIONAL,"21.23");var tax=call(creator,post(path(creator)+"/calculated"),direct,201);
        call(creator,get(path(creator)+"/"+tax.at("/taxObligation/id").asText()+"/calculation"),null,403);
    }

    @Test void calculatedTaxSerializesSameOperationAndKeepsCancelledTraceImmutable() throws Exception {
        var f=fixture(CurrencyCode.BRL,AccessAction.READ,AccessAction.CREATE_DIRECT,AccessAction.CANCEL_DIRECT);
        var body=calculatedBody(f,TaxTreatment.INFORMATIONAL,"21.23");
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return mvc.perform(post(path(f)+"/calculated").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return mvc.perform(post(path(f)+"/calculated").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();});
            gate.countDown();var codes=new java.util.ArrayList<Integer>(List.of(a.get(60,java.util.concurrent.TimeUnit.SECONDS),b.get(60,java.util.concurrent.TimeUnit.SECONDS)));codes.sort(Integer::compareTo);assertEquals(List.of(200,201),codes);
        }
        String id=call(f,post(path(f)+"/calculated"),body,200).at("/taxObligation/id").asText();
        call(f,post("/api/v1/company/invoices/"+f.invoiceId()+"/cancellation"),Map.of("reason","Cancelamento sintético"),200);
        assertEquals("2.1234",call(f,get(path(f)+"/"+id+"/calculation"),null,200).path("ratePercent").asText());
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("delete from invoice_tax_operation where company_id=?",f.companyId()));
        assertThrows(DataIntegrityViolationException.class,()->jdbc.update("update invoice_tax_operation set rate_percent=3 where company_id=?",f.companyId()));
    }

    @Test void calculatedTaxConcurrentWithholdingsCannotExceedOfficialOutstandingAmount() throws Exception {
        var f=fixture(CurrencyCode.BRL,AccessAction.READ,AccessAction.CREATE_DIRECT);
        var first=new HashMap<String,Object>(calculatedBody(f,TaxTreatment.WITHHELD_BY_CUSTOMER,"70.00"));first.put("calculation",Map.of("mode","AMOUNT","amount","70.00"));
        var second=new HashMap<String,Object>(first);second.put("operationId",UUID.randomUUID());
        try(var pool=java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var gate=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{gate.await();return mvc.perform(post(path(f)+"/calculated").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(first))).andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{gate.await();return mvc.perform(post(path(f)+"/calculated").with(jwt().jwt(t->t.subject(f.userId().toString()))).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(second))).andReturn().getResponse().getStatus();});
            gate.countDown();var codes=new java.util.ArrayList<Integer>(List.of(a.get(60,java.util.concurrent.TimeUnit.SECONDS),b.get(60,java.util.concurrent.TimeUnit.SECONDS)));codes.sort(Integer::compareTo);assertEquals(List.of(201,409),codes);
        }
        assertEquals(new BigDecimal("30.00"),balances.calculate(f.companyId(),f.receivableId()).outstandingAmount());
        assertEquals(1,call(f,get(path(f)),null,200).path("totalItems").asInt());
    }

    private Map<String,Object> percentage() {return Map.of("mode","PERCENTAGE","baseAmount","1000.00","ratePercent","2.1234");}
    private Map<String,Object> calculatedBody(Fixture f,TaxTreatment treatment,String confirmed) {
        Map<String,Object> b=new HashMap<>();b.put("operationId",UUID.randomUUID());b.put("name","Tributo conferido");b.put("treatment",treatment.name());b.put("calculation",percentage());b.put("confirmedAmount",confirmed);
        if(treatment==TaxTreatment.WITHHELD_BY_CUSTOMER)b.put("receivableId",f.receivableId());if(treatment==TaxTreatment.PAYABLE_BY_COMPANY)b.put("dueOn","2026-11-25");return b;
    }
    private Fixture taxActor(Fixture f,AccessAction... actions) {
        User u=User.create(UUID.randomUUID()+"@example.test",UserType.COMPANY);u.activate(hasher.hash("Canteiro#2026Seguro"),Instant.now());u=users.save(u);memberships.save(CompanyUser.create(u.getId(),f.companyId()));
        var role=roles.save(Role.create(f.companyId(),"Auditor "+UUID.randomUUID(),null));userRoles.save(UserRole.create(u.getId(),role.getId(),f.companyId()));
        var result=new Fixture(f.companyId(),u.getId(),role.getId(),f.workId(),f.contractId(),f.invoiceId(),f.receivableId());for(var action:actions)grant(result,AccessModule.INVOICES,action);return result;
    }

    @Test void exportsExecutableTaxReadContractWithNullabilityAndCanonicalDecimal() throws Exception {
        String specification = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var root = json.readTree(specification);
        String path = "/api/v1/company/invoices/{invoiceId}/tax-obligations";
        assertEquals("#/components/schemas/PageResponseInvoiceTaxObligationResponse", root.path("paths").path(path).at("/get/responses/200/content/application~1json/schema/$ref").asText());
        var properties = root.at("/components/schemas/InvoiceTaxObligationResponse/properties");
        assertEquals("string", properties.at("/amount/type").asText()); assertEquals("decimal", properties.at("/amount/format").asText());
        assertTrue(properties.path("description").toString().contains("\"null\"")); assertTrue(properties.path("dueOn").toString().contains("\"null\""));
        assertFalse(properties.has("receivableId")); assertFalse(properties.has("payableId"));
        assertEquals(10, root.at("/components/schemas/InvoiceTaxObligationResponse/required").size());
        String export = System.getenv("INVOICE_TAX_OPENAPI_EXPORT_PATH");
        if (export != null && !export.isBlank()) {
            Path target = Path.of(export).toAbsolutePath().normalize(); Files.createDirectories(target.getParent()); Files.writeString(target, specification);
        }
    }

    private void registerAll(Fixture f) throws Exception {
        for (TaxTreatment treatment : TaxTreatment.values()) register(f, "Tributo " + treatment.name(), treatment, "10.00", 201);
    }

    private JsonNode register(Fixture f, String name, TaxTreatment treatment, String amount, int expected) throws Exception {
        Map<String, Object> body = new HashMap<>(); body.put("name", name); body.put("amount", amount); body.put("treatment", treatment.name());
        if (treatment == TaxTreatment.PAYABLE_BY_COMPANY) body.put("dueOn", "2026-11-25");
        if (treatment == TaxTreatment.WITHHELD_BY_CUSTOMER) body.put("receivableId", f.receivableId());
        return call(f, post(path(f)), body, expected);
    }

    private JsonNode call(Fixture f, MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        request.with(jwt().jwt(token -> token.subject(f.userId().toString())));
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var result = mvc.perform(request).andExpect(status().is(expected));
        if (expected == 200 && body == null) result.andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        if (expected >= 400) result.andExpect(content().contentTypeCompatibleWith("application/problem+json")).andExpect(jsonPath("$.correlationId").isNotEmpty());
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<Map<String, Object>> snapshot(Fixture f) {
        return jdbc.queryForList("""
                select 'tax' kind,id::text,to_jsonb(t)::text data from tax_obligation t where company_id=?
                union all select 'invoice',id::text,to_jsonb(i)::text from invoice i where company_id=?
                union all select 'item',id::text,to_jsonb(i)::text from invoice_item i where company_id=?
                union all select 'allocation',id::text,to_jsonb(a)::text from billing_allocation a where company_id=?
                union all select 'installment',id::text,to_jsonb(b)::text from billing_installment b where company_id=?
                union all select 'receivable',id::text,to_jsonb(r)::text from receivable r where company_id=?
                union all select 'settlement',id::text,to_jsonb(s)::text from receivable_settlement s where company_id=?
                union all select 'payable',id::text,to_jsonb(p)::text from payable p where company_id=?
                union all select 'audit',id::text,to_jsonb(a)::text from audit_event a where company_id=?
                union all select 'request',id::text,to_jsonb(c)::text from change_request c where company_id=? order by 1,2
                """, f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId(), f.companyId());
    }

    private Receivable title(UUID companyId, UUID contractId) {
        var installment = installments.save(BillingInstallment.createApproved(companyId, contractId, "PARC", null, null, LocalDate.of(2026, 11, 25)));
        return receivables.save(Receivable.create(companyId, contractId, installment.getId(), UUID.randomUUID(), "REC", null, null,
                LocalDate.of(2026, 11, 25), new BigDecimal("100.00")));
    }

    private Fixture fixture(CurrencyCode currency, AccessAction... actions) {
        String suffix = UUID.randomUUID().toString();
        var company = companies.save(Company.create("Emitente sintética", null, Long.toUnsignedString(UUID.randomUUID().getLeastSignificantBits()), suffix + "@example.test", null, null, null));
        var customer = customers.save(FinalCustomer.create(company.getId(), FinalCustomerType.LEGAL, "Cliente sintético", "12345678000195"));
        var work = works.save(Work.create(company.getId(), customer.getId(), "Obra", null, WorkExecutionLocationType.FINAL_CUSTOMER_LOCATION, null, WorkStatus.ACTIVE, null, null, null));
        var contract = contracts.save(Contract.create(company.getId(), work.getId(), "Contrato de tributos", ContractPricingMode.FIXED, null, "Contrato", ContractStatus.ACTIVE, null, null, null, currency));
        var invoice = invoices.save(Invoice.register(company.getId(), contract.getId(), "NF-TRIB", null, "private-fiscal-key", LocalDate.of(2026, 10, 26), new BigDecimal("100.00")));
        var receivable = title(company.getId(), contract.getId());
        var service = services.save(com.renovar.canteiro.io.contracts.domain.ContractService.create(company.getId(), contract.getId(),
                "Serviço sintético", null, com.renovar.canteiro.io.contracts.domain.ContractServiceStatus.ACTIVE,
                BigDecimal.ONE, new BigDecimal("100.00")));
        var allocation = allocations.save(com.renovar.canteiro.io.receivables.domain.BillingAllocation.createService(company.getId(),
                receivable.getBillingInstallmentId(), contract.getId(), service.getId(), new BigDecimal("100.00")).activate());
        invoiceItems.save(com.renovar.canteiro.io.receivables.domain.InvoiceItem.create(company.getId(), invoice.getId(), contract.getId(),
                allocation.getId(), service.getId(), "Serviço sintético", new BigDecimal("100.00")));
        User user = User.create(suffix + "@example.test", UserType.COMPANY); user.activate(hasher.hash("Canteiro#2026Seguro"), Instant.now()); user = users.save(user);
        memberships.save(CompanyUser.create(user.getId(), company.getId()));
        var role = roles.save(Role.create(company.getId(), "Operador", null)); userRoles.save(UserRole.create(user.getId(), role.getId(), company.getId()));
        var f = new Fixture(company.getId(), user.getId(), role.getId(), work.getId(), contract.getId(), invoice.getId(), receivable.getId());
        for (AccessAction action : actions) grant(f, AccessModule.INVOICES, action);
        return f;
    }

    private void grant(Fixture f, AccessModule module, AccessAction action) {
        var permission = permissions.findByModuleAndAction(module, action).orElseThrow(); rolePermissions.save(RolePermission.create(f.roleId(), permission.getId()));
    }

    private String path(Fixture f) { return "/api/v1/company/invoices/" + f.invoiceId() + "/tax-obligations"; }
    private record Fixture(UUID companyId, UUID userId, UUID roleId, UUID workId, UUID contractId, UUID invoiceId, UUID receivableId) { }
}
