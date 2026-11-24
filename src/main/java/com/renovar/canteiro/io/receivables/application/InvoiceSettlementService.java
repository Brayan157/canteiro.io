package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceStatus;
import com.renovar.canteiro.io.receivables.domain.InvoiceSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.InvoiceWithholdingCapacity;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableBalance;
import com.renovar.canteiro.io.receivables.domain.ReceivableOriginRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlement;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementPaymentMethod;
import com.renovar.canteiro.io.receivables.domain.ReceivableSettlementRepository;
import com.renovar.canteiro.io.receivables.domain.ReceivableStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.api.error.FieldViolation;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvoiceSettlementService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final InvoiceRepository invoices;
    private final ReceivableRepository titles;
    private final ReceivableSettlementRepository settlements;
    private final ReceivableOriginRepository origins;
    private final InvoiceSettlementRepository ledger;
    private final com.renovar.canteiro.io.measurements.domain.MeasurementContractCompositionRepository compositions;
    private final CalculateReceivableBalanceUseCase balances;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final AuditEventRecorder audits;
    private final Clock clock;
    private final tools.jackson.databind.ObjectMapper json;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Options options(UUID receivableId, Pageable pageable) {
        UUID company = tenants.requireCurrentTenant().companyId();
        authorization.requirePermission(AccessModule.RECEIVABLES, AccessAction.READ);
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        if (pageable.getOffset() > Integer.MAX_VALUE) throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_PAGINATION, "Page offset is too large.");
        var title = title(company, receivableId);
        var balance = balances.calculate(company, receivableId);
        String reason = unavailable(company, title, balance);
        CurrencyCode currency = contracts.findByIdAndCompanyId(title.getContractId(), company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract")).getCurrency();
        if (reason != null) return new Options(receivableId, title.getContractId(), currency, false, reason, today(), balance, Page.empty(pageable), TotalProposal.unavailable(reason));
        var eligibleOrigins = eligibleOrigins(company, title.getContractId());
        var candidates = ledger.candidates(company, receivableId, pageable);
        var capacities = capacities(company, receivableId, candidates.getContent().stream().map(InvoiceSettlementRepository.Candidate::invoiceId).toList());
        var page = candidates.map(candidate -> {
            boolean originAccepted = candidate.measurementContractCompositionId() == null || eligibleOrigins.contains(candidate.measurementContractCompositionId());
            String itemReason = !originAccepted ? "SETTLEMENT_ORIGIN_NOT_ACCEPTED"
                    : ledger.hasUnprovenInvoiceHistory(company, candidate.invoiceId()) ? "INVOICE_HISTORY_REQUIRES_RECONCILIATION" : null;
            var itemCapacity = itemReason == null ? capacity(capacities, candidate) : InvoiceWithholdingCapacity.Item.unavailable(itemReason);
            if (itemReason == null) itemReason = itemCapacity.unavailableReason();
            BigDecimal max = itemReason == null ? itemCapacity.availableAmount()
                    .min(candidate.invoiceNetAmount().subtract(candidate.invoiceReceivedAmount())).min(balance.outstandingAmount()).max(BigDecimal.ZERO) : null;
            return new Option(candidate, itemReason == null && max.signum() > 0, max,
                    itemReason != null ? itemReason : max.signum() > 0 ? null : "SETTLEMENT_NO_ITEM_CAPACITY", itemCapacity);
        });
        return new Options(receivableId, title.getContractId(), currency, true, null, today(), balance, page,
                totalProposal(company, title, balance, eligibleOrigins));
    }

    private TotalProposal totalProposal(UUID company, Receivable title, ReceivableBalance balance, java.util.Set<UUID> eligibleOrigins) {
        var candidates = ledger.candidates(company, title.getId(), org.springframework.data.domain.PageRequest.of(0, 100));
        if (candidates.getTotalElements() > 100) return TotalProposal.unavailable("SETTLEMENT_TOTAL_TOO_MANY_ITEMS");
        var capacities = capacities(company, title.getId(), candidates.getContent().stream().map(InvoiceSettlementRepository.Candidate::invoiceId).toList());
        Map<UUID, BigDecimal> remaining = new HashMap<>();
        Map<UUID, List<InvoiceSettlementRepository.Candidate>> groups = new LinkedHashMap<>();
        for (var item : candidates) {
            var itemCapacity = capacity(capacities, item);
            if (itemCapacity.unavailableReason() != null) return TotalProposal.unavailable(itemCapacity.unavailableReason());
            if (itemCapacity.availableAmount().signum() <= 0) continue;
            remaining.put(item.invoiceItemId(), itemCapacity.availableAmount());
            if (item.measurementContractCompositionId() != null && !eligibleOrigins.contains(item.measurementContractCompositionId()))
                return TotalProposal.unavailable("SETTLEMENT_ORIGIN_NOT_ACCEPTED");
            if (ledger.hasUnprovenInvoiceHistory(company, item.invoiceId())) return TotalProposal.unavailable("INVOICE_HISTORY_REQUIRES_RECONCILIATION");
            groups.computeIfAbsent(item.invoiceId(), ignored -> new ArrayList<>()).add(item);
        }
        Map<UUID, BigDecimal> budgets = new LinkedHashMap<>();
        BigDecimal capacity = BigDecimal.ZERO;
        int positiveItems = 0;
        for (var entry : groups.entrySet()) {
            BigDecimal sum = entry.getValue().stream().map(item -> remaining.get(item.invoiceItemId())).reduce(BigDecimal.ZERO, BigDecimal::add);
            var first = entry.getValue().getFirst();
            BigDecimal budget = first.invoiceNetAmount().subtract(first.invoiceReceivedAmount()).max(BigDecimal.ZERO).min(sum);
            if (budget.signum() > 0) { budgets.put(entry.getKey(), budget); capacity = capacity.add(budget); positiveItems += entry.getValue().size(); }
        }
        BigDecimal target = balance.outstandingAmount();
        if (target.compareTo(capacity) > 0) return TotalProposal.unavailable("SETTLEMENT_TOTAL_INVOICE_COVERAGE_REQUIRED");
        if (target.compareTo(capacity) < 0 && positiveItems != 1) return TotalProposal.unavailable("SETTLEMENT_TOTAL_DISTRIBUTION_REQUIRED");
        List<CreateInvoiceSettlementCommand.Line> lines = new ArrayList<>();
        for (var entry : budgets.entrySet()) {
            var items = groups.get(entry.getKey());
            BigDecimal sum = items.stream().map(item -> remaining.get(item.invoiceItemId())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal budget = positiveItems == 1 ? target : entry.getValue();
            if (items.size() > 1 && budget.compareTo(sum) != 0) return TotalProposal.unavailable("SETTLEMENT_TOTAL_DISTRIBUTION_REQUIRED");
            for (var item : items) lines.add(new CreateInvoiceSettlementCommand.Line(item.invoiceId(), item.invoiceItemId(), item.billingAllocationId(),
                    item.measurementContractCompositionId(), items.size() == 1 ? budget : remaining.get(item.invoiceItemId())));
        }
        // Exact remaining net capacities, after authorized withholding allocation; cash partials are never distributed.
        return new TotalProposal(true, null, target, List.copyOf(lines));
    }

    @Transactional
    public Result create(CreateInvoiceSettlementCommand command) {
        UUID company = tenants.requireCurrentTenant().companyId();
        var mode = authorization.requireChangeAuthorization(AccessModule.RECEIVABLES, ChangeOperation.CREATE);
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        validateInput(command);
        ledger.lockOperation(company, command.operationId());
        String fingerprint = fingerprint(command);
        var existing = ledger.findOperation(company, command.operationId());
        if (existing.isPresent()) {
            var intent = existing.get();
            if (!intent.receivableId().equals(command.receivableId()) || !intent.fingerprint().equals(fingerprint))
                throw conflict("SETTLEMENT_OPERATION_CONFLICT", "Operation ID already used with different receipt inputs.");
            if (intent.settlementId() != null) return result(company, intent.settlementId(), true);
            var request = requests.findByIdAndCompanyId(intent.requestId(), company).orElseThrow();
            if (request.getStatus() != ChangeRequestStatus.PENDING) throw conflict("SETTLEMENT_PROPOSAL_DECIDED", "Use a new operation ID after a rejected or decided proposal.");
            return new Result(null, null, List.of(), request, ChangeAuthorizationMode.REQUEST_APPROVAL, true);
        }
        var title = lock(company, command);
        validatePosition(company, title, command);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            var request = changes.create(new CreateChangeRequestCommand(AuditModule.RECEIVABLES, ChangeRequestOperation.CREATE,
                    "InvoiceSettlement", command.receivableId(), 0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()));
            ledger.remember(company, command.operationId(), title.getId(), title.getContractId(), fingerprint, null, request.getId());
            return new Result(null, null, List.of(), request, mode, false);
        }
        var settlement = persist(company, title, command, null);
        ledger.remember(company, command.operationId(), title.getId(), title.getContractId(), fingerprint, settlement.getId(), null);
        return result(company, settlement.getId(), false);
    }

    @Transactional
    public void apply(ChangeRequest request) {
        authorization.requirePermission(AccessModule.INVOICES, AccessAction.READ);
        var command = fromProposal(request.getSnapshot().proposedData().values());
        validateInput(command);
        ledger.lockOperation(request.getCompanyId(), command.operationId());
        var intent = ledger.findOperation(request.getCompanyId(), command.operationId()).orElseThrow();
        if (!request.getId().equals(intent.requestId()) || intent.settlementId() != null || !intent.fingerprint().equals(fingerprint(command)))
            throw conflict("SETTLEMENT_OPERATION_CONFLICT", "Proposal no longer matches its receipt intent.");
        var title = lock(request.getCompanyId(), command);
        validatePosition(request.getCompanyId(), title, command);
        var settlement = persist(request.getCompanyId(), title, command, request.getId());
        ledger.complete(request.getCompanyId(), command.operationId(), request.getId(), settlement.getId());
    }

    private Receivable lock(UUID company, CreateInvoiceSettlementCommand command) {
        var source = title(company, command.receivableId());
        contracts.findByIdAndCompanyIdWithCancellationCheckLock(source.getContractId(), company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        for (UUID invoiceId : command.items().stream().map(CreateInvoiceSettlementCommand.Line::invoiceId).distinct().sorted().toList()) {
            var invoice = invoices.findByIdAndCompanyIdWithLock(invoiceId, company).filter(value -> source.getContractId().equals(value.getContractId()))
                    .orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
            if (invoice.getStatus() != InvoiceStatus.REGISTERED) throw conflict("SETTLEMENT_INVOICE_NOT_REGISTERED", "Only a registered invoice can receive a new linked receipt.");
        }
        origins.lockInstallment(company, source.getBillingInstallmentId());
        return titles.findByIdAndCompanyIdWithCancellationCheckLock(source.getId(), company).orElseThrow();
    }

    private void validatePosition(UUID company, Receivable title, CreateInvoiceSettlementCommand command) {
        validateDate(command.receivedOn());
        var balance = balances.calculate(company, title.getId());
        String reason = unavailable(company, title, balance);
        if (reason != null) throw conflict(reason, "This title is not eligible for an explicitly linked receipt.");
        if (title.getIssuedOn() != null && command.receivedOn().isBefore(title.getIssuedOn())) throw invalid("receivedOn", "Must not precede the title issue date.");
        if (command.amount().compareTo(balance.outstandingAmount()) > 0) throw conflict("SETTLEMENT_AMOUNT_EXCEEDS_TITLE", "Receipt exceeds the official title balance.");
        Map<UUID, BigDecimal> perInvoice = new HashMap<>();
        Map<UUID, BigDecimal> invoiceLimits = new HashMap<>();
        var eligibleOrigins = eligibleOrigins(company, title.getContractId());
        var capacities = capacities(company, title.getId(), command.items().stream().map(CreateInvoiceSettlementCommand.Line::invoiceId).toList());
        for (var line : command.items()) {
            var candidate = ledger.candidate(company, title.getId(), line.invoiceItemId()).orElseThrow(() -> new TenantResourceNotFoundException("Invoice item"));
            if (!candidate.invoiceId().equals(line.invoiceId()) || !candidate.billingAllocationId().equals(line.billingAllocationId())
                    || !Objects.equals(candidate.measurementContractCompositionId(), line.measurementContractCompositionId()))
                throw new TenantResourceNotFoundException("Invoice item origin");
            if (line.measurementContractCompositionId() != null && !eligibleOrigins.contains(line.measurementContractCompositionId()))
                throw conflict("SETTLEMENT_ORIGIN_NOT_ACCEPTED", "Only an effective accepted measurement origin can receive cash.");
            if (ledger.hasUnprovenInvoiceHistory(company, line.invoiceId())) throw conflict("INVOICE_HISTORY_REQUIRES_RECONCILIATION", "Invoice history requires reconciliation.");
            var itemCapacity = capacity(capacities, candidate);
            if (itemCapacity.unavailableReason() != null) throw conflict(itemCapacity.unavailableReason(), "Invoice withholding requires reconciliation before another receipt.");
            if (line.amount().compareTo(itemCapacity.availableAmount()) > 0)
                throw conflict("SETTLEMENT_AMOUNT_EXCEEDS_ITEM", "Receipt exceeds the official invoice item capacity.");
            perInvoice.merge(line.invoiceId(), line.amount(), BigDecimal::add);
            invoiceLimits.put(line.invoiceId(), candidate.invoiceNetAmount().subtract(candidate.invoiceReceivedAmount()));
        }
        perInvoice.forEach((id, amount) -> { if (amount.compareTo(invoiceLimits.get(id)) > 0)
            throw conflict("SETTLEMENT_AMOUNT_EXCEEDS_INVOICE", "Receipt exceeds the official invoice net balance."); });
    }

    private Map<UUID, InvoiceWithholdingCapacity> capacities(UUID company, UUID receivable, List<UUID> invoiceIds) {
        return InvoiceWithholdingCapacity.calculate(ledger.withholdingBasis(company, receivable, invoiceIds.stream().distinct().toList()));
    }

    private InvoiceWithholdingCapacity.Item capacity(Map<UUID, InvoiceWithholdingCapacity> capacities, InvoiceSettlementRepository.Candidate candidate) {
        var invoice = capacities.get(candidate.invoiceId());
        return invoice == null ? InvoiceWithholdingCapacity.Item.unavailable("SETTLEMENT_WITHHOLDING_BASIS_REQUIRES_RECONCILIATION") : invoice.item(candidate);
    }

    private String unavailable(UUID company, Receivable title, ReceivableBalance balance) {
        if (title.getStatus() == ReceivableStatus.CANCELLED || title.getStatus() == ReceivableStatus.RENEGOTIATED) return "SETTLEMENT_TITLE_INACTIVE";
        if (titles.findAllByBillingInstallmentIdAndCompanyId(title.getBillingInstallmentId(), company).size() != 1) return "SETTLEMENT_RENEGOTIATION_REQUIRES_RECONCILIATION";
        if (balance.creditedAmount().signum() > 0) return "SETTLEMENT_CREDIT_REQUIRES_RECONCILIATION";
        if (ledger.hasUnprovenHistory(company, title.getId())) return "SETTLEMENT_HISTORY_REQUIRES_RECONCILIATION";
        return balance.outstandingAmount().signum() <= 0 ? "SETTLEMENT_NO_OUTSTANDING_BALANCE" : null;
    }

    private ReceivableSettlement persist(UUID company, Receivable title, CreateInvoiceSettlementCommand command, UUID requestId) {
        var settlement = settlements.save(ReceivableSettlement.confirm(company, title.getId(), title.getContractId(), command.receivedOn(), command.amount(), command.paymentMethod(), command.note()));
        Map<UUID, BigDecimal> perAllocation = new LinkedHashMap<>();
        for (var line : command.items()) {
            ledger.append(new InvoiceSettlementRepository.Line(UUID.randomUUID(), company, title.getContractId(), title.getId(), settlement.getId(),
                    line.invoiceId(), line.invoiceItemId(), line.billingAllocationId(), line.measurementContractCompositionId(), line.amount(), null, null));
            perAllocation.merge(line.billingAllocationId(), line.amount(), BigDecimal::add);
        }
        // Explicit human amounts only; proportional attribution is never called for this flow.
        perAllocation.forEach((allocation, amount) -> origins.attributeSettlement(company, settlement.getId(), allocation, amount));
        var balance = balances.calculate(company, title.getId());
        var updatedTitle = titles.updateStatus(title.withStatus(balance.statusOn(title, today())));
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("origin", requestId == null ? "direct" : "approved-change-request");
        metadata.put("operationId", command.operationId().toString());
        if (requestId != null) metadata.put("changeRequestId", requestId.toString());
        audits.recordDirectAction(AuditModule.RECEIVABLES, AuditAction.CREATE, "ReceivableSettlement", settlement.getId(), null,
                Map.of("receivableId", title.getId().toString(), "contractId", title.getContractId().toString(), "amount", command.amount().toPlainString(),
                        "items", command.items().stream().map(line -> Map.of("invoiceId", line.invoiceId().toString(), "invoiceItemId", line.invoiceItemId().toString(),
                                "billingAllocationId", line.billingAllocationId().toString(), "amount", line.amount().toPlainString())).toList()), metadata);
        if (title.getStatus() != updatedTitle.getStatus()) audits.recordDirectAction(AuditModule.RECEIVABLES, AuditAction.UPDATE, "Receivable", title.getId(),
                Map.of("status", title.getStatus().name()), Map.of("status", updatedTitle.getStatus().name(), "settlementId", settlement.getId().toString()), metadata);
        return settlement;
    }

    private Result result(UUID company, UUID settlementId, boolean replayed) {
        var settlement = settlements.findByIdAndCompanyId(settlementId, company).orElseThrow();
        return new Result(settlement, balances.calculate(company, settlement.getReceivableId()), ledger.lines(company, settlementId), null, ChangeAuthorizationMode.DIRECT, replayed);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForReversal(UUID company, ReceivableSettlement original) {
        var title = title(company, original.getReceivableId());
        contracts.findByIdAndCompanyIdWithCancellationCheckLock(title.getContractId(), company).orElseThrow();
        for (UUID id : ledger.lines(company, original.getId()).stream().map(InvoiceSettlementRepository.Line::invoiceId).distinct().sorted().toList())
            invoices.findByIdAndCompanyIdWithLock(id, company).orElseThrow(() -> new TenantResourceNotFoundException("Invoice"));
        origins.lockInstallment(company, title.getBillingInstallmentId());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordReversal(UUID company, ReceivableSettlement original, ReceivableSettlement reversal) {
        for (var line : ledger.lines(company, original.getId())) ledger.append(new InvoiceSettlementRepository.Line(UUID.randomUUID(), company,
                line.contractId(), line.receivableId(), reversal.getId(), line.invoiceId(), line.invoiceItemId(), line.billingAllocationId(),
                line.measurementContractCompositionId(), line.amount().negate(), line.id(), null));
    }

    private Receivable title(UUID company, UUID id) { return titles.findByIdAndCompanyId(id, company).orElseThrow(() -> new TenantResourceNotFoundException("Receivable")); }
    private java.util.Set<UUID> eligibleOrigins(UUID company, UUID contract) {
        return compositions.findActiveContractFinancialOrigins(company, contract).stream()
                .filter(com.renovar.canteiro.io.measurements.domain.MeasurementContractFinancialOrigin::eligible)
                .map(com.renovar.canteiro.io.measurements.domain.MeasurementContractFinancialOrigin::id).collect(java.util.stream.Collectors.toSet());
    }
    private LocalDate today() { return LocalDate.now(clock.withZone(ZoneOffset.UTC)); }
    private void validateDate(LocalDate date) { if (date.isAfter(today())) throw invalid("receivedOn", "Must not be later than the current UTC calendar date."); }
    private void validateInput(CreateInvoiceSettlementCommand command) {
        if (command == null || command.operationId() == null || command.receivableId() == null || command.receivedOn() == null || command.paymentMethod() == null
                || command.items() == null || command.items().isEmpty() || command.items().size() > 100) throw invalid("items", "Required receipt identity and 1 to 100 manual lines.");
        validateDate(command.receivedOn()); money(command.amount(), "amount");
        if (command.note() != null && command.note().length() > 1000) throw invalid("note", "Maximum 1000 characters.");
        if (command.justification() != null && command.justification().length() > 1000) throw invalid("justification", "Maximum 1000 characters.");
        var ids = new HashSet<UUID>(); BigDecimal sum = BigDecimal.ZERO;
        for (var line : command.items()) {
            if (line == null || line.invoiceId() == null || line.invoiceItemId() == null || line.billingAllocationId() == null || !ids.add(line.invoiceItemId()))
                throw invalid("items", "Each exact invoice item must occur once.");
            money(line.amount(), "items.amount"); sum = sum.add(line.amount());
        }
        if (sum.compareTo(command.amount()) != 0) throw invalid("amount", "Must equal the sum of the manual item amounts.");
    }
    private void money(BigDecimal value, String field) {
        try { if (value == null || value.signum() <= 0 || value.setScale(2, RoundingMode.UNNECESSARY).precision() > 19) throw invalid(field, "Positive money with at most 17 integer and two decimal places is required."); }
        catch (ArithmeticException invalidScale) { throw invalid(field, "At most two decimal places are allowed."); }
    }
    private Map<String, Object> proposal(CreateInvoiceSettlementCommand command) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operationId", command.operationId().toString()); result.put("receivableId", command.receivableId().toString()); result.put("receivedOn", command.receivedOn().toString());
        result.put("amount", command.amount().setScale(2).toPlainString()); result.put("paymentMethod", command.paymentMethod().name()); result.put("note", command.note()); result.put("justification", command.justification());
        result.put("items", command.items().stream().sorted(Comparator.comparing(CreateInvoiceSettlementCommand.Line::invoiceItemId)).map(line -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("invoiceId", line.invoiceId().toString()); row.put("invoiceItemId", line.invoiceItemId().toString());
            row.put("billingAllocationId", line.billingAllocationId().toString()); row.put("measurementContractCompositionId", line.measurementContractCompositionId() == null ? null : line.measurementContractCompositionId().toString());
            row.put("amount", line.amount().setScale(2).toPlainString()); return row;
        }).toList());
        return result;
    }
    private String fingerprint(CreateInvoiceSettlementCommand command) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsString(proposal(command)).getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    @SuppressWarnings("unchecked")
    private CreateInvoiceSettlementCommand fromProposal(Map<String, Object> data) {
        var lines = new ArrayList<CreateInvoiceSettlementCommand.Line>();
        for (var row : (List<Map<String, Object>>) data.get("items")) lines.add(new CreateInvoiceSettlementCommand.Line(UUID.fromString((String) row.get("invoiceId")), UUID.fromString((String) row.get("invoiceItemId")),
                UUID.fromString((String) row.get("billingAllocationId")), row.get("measurementContractCompositionId") == null ? null : UUID.fromString((String) row.get("measurementContractCompositionId")), new BigDecimal((String) row.get("amount"))));
        return new CreateInvoiceSettlementCommand(UUID.fromString((String) data.get("operationId")), UUID.fromString((String) data.get("receivableId")), LocalDate.parse((String) data.get("receivedOn")),
                new BigDecimal((String) data.get("amount")), ReceivableSettlementPaymentMethod.valueOf((String) data.get("paymentMethod")), (String) data.get("note"), (String) data.get("justification"), lines);
    }
    private ApiException invalid(String field, String message) { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, message, List.of(new FieldViolation(field, message))); }
    private ApiException conflict(String reason, String message) { return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message, List.of(), reason); }
    public record Option(InvoiceSettlementRepository.Candidate item, boolean eligible, BigDecimal maximumLineAmount, String unavailableReason, InvoiceWithholdingCapacity.Item capacity) { }
    public record TotalProposal(boolean available, String unavailableReason, BigDecimal amount, List<CreateInvoiceSettlementCommand.Line> items) {
        static TotalProposal unavailable(String reason) { return new TotalProposal(false, reason, null, List.of()); }
    }
    public record Options(UUID receivableId, UUID contractId, CurrencyCode currency, boolean eligible, String unavailableReason, LocalDate maxReceivedOn, ReceivableBalance balance, Page<Option> items, TotalProposal totalSettlementProposal) { }
    public record Result(ReceivableSettlement settlement, ReceivableBalance balance, List<InvoiceSettlementRepository.Line> items, ChangeRequest changeRequest, ChangeAuthorizationMode mode, boolean replayed) { }
}
