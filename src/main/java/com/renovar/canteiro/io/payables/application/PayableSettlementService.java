package com.renovar.canteiro.io.payables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
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
import com.renovar.canteiro.io.payables.domain.Payable;
import com.renovar.canteiro.io.payables.domain.PayableCatalogValues;
import com.renovar.canteiro.io.payables.domain.PayablePaymentMethod;
import com.renovar.canteiro.io.payables.domain.PayableRepository;
import com.renovar.canteiro.io.payables.domain.PayableSettlement;
import com.renovar.canteiro.io.payables.domain.PayableSettlementRepository;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PayableSettlementService {
    static final String ENTITY_TYPE = "PayableSettlement";

    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final PayableRepository payables;
    private final PayableSettlementRepository ledger;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final AuditEventRecorder audit;
    private final ObjectMapper json;
    private final Clock clock;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<PayableSettlement> history(UUID payableId, Pageable page) {
        authorization.requirePermission(AccessModule.PAYABLES, AccessAction.READ);
        UUID company = tenants.requireCurrentTenant().companyId();
        payables.findByIdAndCompanyId(payableId, company).orElseThrow(() -> new TenantResourceNotFoundException("Payable"));
        return ledger.history(company, payableId, page);
    }

    @Transactional
    public Result submit(PayableSettlementCommand command) {
        authorization.requirePermission(AccessModule.PAYABLES, AccessAction.READ);
        boolean reversal = command.reversalOfId() != null;
        var mode = authorization.requireChangeAuthorization(AccessModule.PAYABLES,
                reversal ? ChangeOperation.CANCEL : ChangeOperation.CREATE);
        var tenant = tenants.requireCurrentTenant();
        UUID company = tenant.companyId();
        ledger.lockOperation(company, command.operationId());
        Payable title = lockTitle(company, command.payableId());
        String justification = PayableCatalogValues.text(command.justification(), 1000, reversal);
        var normalized = new PayableSettlementCommand(command.operationId(), command.payableId(), command.reversalOfId(),
                command.amount(), command.effectiveOn(), command.paymentMethod(), justification);
        String fingerprint = fingerprint(normalized);
        var existing = ledger.findOperation(company, command.operationId());
        if (existing.isPresent()) return replay(title, normalized, existing.get(), fingerprint);

        var current = position(title);
        var entry = prepare(current, normalized);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            var request = changes.create(new CreateChangeRequestCommand(AuditModule.PAYABLES,
                    reversal ? ChangeRequestOperation.CANCEL : ChangeRequestOperation.CREATE, ENTITY_TYPE,
                    command.operationId(), title.getVersion(),
                    new ChangeRequestSnapshot(null, new AuditPayload(Map.of("command", json.writeValueAsString(normalized),
                            "title", reversal ? "Estorno de pagamento" : "Baixa de conta a pagar"))),
                    justification));
            ledger.saveOperation(company, command.operationId(),
                    new PayableSettlementRepository.Operation(title.getId(), fingerprint, request.getId()));
            return new Result(null, null, request.getId(), mode, false);
        }
        ledger.saveOperation(company, command.operationId(),
                new PayableSettlementRepository.Operation(title.getId(), fingerprint, null));
        return persist(current, entry, null);
    }

    @Transactional
    public PayableSettlement apply(ChangeRequest request) {
        var tenant = tenants.requireCurrentTenant();
        if (!tenant.companyId().equals(request.getCompanyId())) throw new TenantResourceNotFoundException("Payable proposal");
        var command = json.readValue((String) request.getSnapshot().proposedData().values().get("command"),
                PayableSettlementCommand.class);
        if (!command.operationId().equals(request.getEntityId())
                || (command.reversalOfId() != null) != (request.getOperation() == ChangeRequestOperation.CANCEL)) {
            throw conflict("PAYABLE_SETTLEMENT_OPERATION_CONFLICT", "Payment proposal does not match its operation.");
        }
        ledger.lockOperation(tenant.companyId(), command.operationId());
        var intent = ledger.findOperation(tenant.companyId(), command.operationId()).orElseThrow();
        if (!request.getId().equals(intent.changeRequestId()) || !command.payableId().equals(intent.payableId())
                || !fingerprint(command).equals(intent.fingerprint())) {
            throw conflict("PAYABLE_SETTLEMENT_OPERATION_CONFLICT", "Payment proposal does not match its operation.");
        }
        var current = position(lockTitle(tenant.companyId(), command.payableId()));
        return persist(current, prepare(current, command), request.getId()).settlement();
    }

    private Result replay(Payable title, PayableSettlementCommand command,
                          PayableSettlementRepository.Operation operation, String fingerprint) {
        if (!title.getId().equals(operation.payableId()) || !fingerprint.equals(operation.fingerprint())) {
            throw conflict("PAYABLE_SETTLEMENT_OPERATION_CONFLICT", "Payment operation already used with different data.");
        }
        var applied = ledger.findApplied(title.getCompanyId(), title.getId(), command.operationId());
        if (applied.isPresent()) return new Result(applied.get(), position(title), null, ChangeAuthorizationMode.DIRECT, true);
        var request = requests.findByIdAndCompanyId(operation.changeRequestId(), title.getCompanyId()).orElseThrow();
        if (request.getStatus() != ChangeRequestStatus.PENDING) {
            throw conflict("PAYABLE_SETTLEMENT_OPERATION_DECIDED", "Payment operation has already been decided.");
        }
        return new Result(null, null, request.getId(), ChangeAuthorizationMode.REQUEST_APPROVAL, true);
    }

    private PayableSettlement prepare(PayablePosition current, PayableSettlementCommand command) {
        Payable title = current.payable();
        UUID company = title.getCompanyId();
        if (command.effectiveOn() == null || command.effectiveOn().isAfter(current.asOf())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Payment or reversal date cannot be in the future.");
        }
        if (!current.available() || !title.acceptsSettlements()) {
            throw conflict("PAYABLE_SETTLEMENT_UNAVAILABLE", "Payment history is unavailable or the payable is closed to settlements.");
        }
        BigDecimal amount;
        PayablePaymentMethod method;
        if (command.reversalOfId() == null) {
            amount = command.amount().setScale(2, RoundingMode.UNNECESSARY);
            method = command.paymentMethod();
            if (amount.signum() <= 0 || method == null) throw new IllegalArgumentException("Positive amount and payment method are required.");
            if (amount.compareTo(current.outstandingAmount()) > 0) {
                throw conflict("PAYABLE_SETTLEMENT_EXCEEDS_BALANCE", "Payment exceeds the outstanding amount.");
            }
        } else {
            var original = ledger.find(company, title.getId(), command.reversalOfId())
                    .orElseThrow(() -> new TenantResourceNotFoundException("Payable settlement"));
            if (original.reversalOfId() != null || ledger.hasReversal(company, title.getId(), original.id())) {
                throw conflict("PAYABLE_SETTLEMENT_ALREADY_REVERSED", "Only an unreversed payment can be reversed.");
            }
            if (command.effectiveOn().isBefore(original.effectiveOn())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED, "Reversal date cannot precede the payment date.");
            }
            amount = original.amount().negate();
            method = original.paymentMethod();
        }
        return new PayableSettlement(UUID.randomUUID(), company, title.getId(), command.operationId(), command.reversalOfId(),
                amount, command.effectiveOn(), method, tenants.requireCurrentTenant().userId(),
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS), command.justification());
    }

    private Result persist(PayablePosition before, PayableSettlement entry, UUID requestId) {
        ledger.save(entry);
        var title = payables.updateStatus(before.payable().withSettlementBalance(before.paidAmount().add(entry.amount()), before.asOf()));
        var after = position(title);
        var metadata = new java.util.LinkedHashMap<String, Object>();
        metadata.put("origin", requestId == null ? "direct" : "approval");
        metadata.put("payableId", title.getId().toString());
        metadata.put("operationId", entry.operationId().toString());
        if (requestId != null) metadata.put("changeRequestId", requestId.toString());
        if (entry.reversalOfId() != null) metadata.put("reversalOfId", entry.reversalOfId().toString());
        audit.recordDirectAction(AuditModule.PAYABLES, entry.reversalOfId() == null ? AuditAction.CREATE : AuditAction.CANCEL,
                ENTITY_TYPE, entry.id(), Map.of("status", before.payable().getStatus().name()),
                Map.of("status", title.getStatus().name(), "effectiveOn", entry.effectiveOn().toString()), metadata);
        return new Result(entry, after, null, ChangeAuthorizationMode.DIRECT, false);
    }

    private Payable lockTitle(UUID company, UUID id) {
        return payables.findByIdAndCompanyIdWithLock(id, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Payable"));
    }

    private PayablePosition position(Payable title) {
        var paid = ledger.netPaid(title.getCompanyId(), List.of(title.getId())).getOrDefault(title.getId(), BigDecimal.ZERO);
        return PayablePosition.calculate(title, paid, LocalDate.now(clock.withZone(ZoneOffset.UTC)));
    }

    private String fingerprint(PayableSettlementCommand command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(command)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static ApiException conflict(String reason, String message) {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message, List.of(), reason);
    }

    public record Result(PayableSettlement settlement, PayablePosition position, UUID changeRequestId,
                         ChangeAuthorizationMode authorizationMode, boolean replay) { }
}
