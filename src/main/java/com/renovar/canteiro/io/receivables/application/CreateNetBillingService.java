package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
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
import com.renovar.canteiro.io.receivables.domain.BillingAllocation;
import com.renovar.canteiro.io.receivables.domain.BillingAllocationRepository;
import com.renovar.canteiro.io.receivables.domain.BillingInstallment;
import com.renovar.canteiro.io.receivables.domain.BillingInstallmentRepository;
import com.renovar.canteiro.io.receivables.domain.NetBillingDistribution;
import com.renovar.canteiro.io.receivables.domain.NetBillingRepository;
import com.renovar.canteiro.io.receivables.domain.Receivable;
import com.renovar.canteiro.io.receivables.domain.ReceivableRepository;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateNetBillingService {
    private final TenantContextHolder tenants;
    private final AccessAuthorizationService authorization;
    private final ContractRepository contracts;
    private final NetBillingCapacityService capacities;
    private final NetBillingRepository operations;
    private final BillingInstallmentRepository installments;
    private final BillingAllocationRepository allocations;
    private final ReceivableRepository receivables;
    private final ValidateContractBillingLimitUseCase limits;
    private final ChangeRequestService changes;
    private final ChangeRequestRepository requests;
    private final AuditEventRecorder audit;
    private final tools.jackson.databind.ObjectMapper json;

    @Transactional
    public Result create(CreateNetBillingCommand command) {
        UUID company = tenants.requireCurrentTenant().companyId();
        var mode = authorization.requireChangeAuthorization(AccessModule.BILLING, ChangeOperation.CREATE);
        requireCommand(command);
        operations.lockOperation(company, command.operationId());
        String fingerprint = fingerprint(command);
        var previous = operations.findOperation(company, command.operationId());
        if (previous.isPresent()) {
            var op = previous.get();
            if (!op.fingerprint().equals(fingerprint) || !op.contractId().equals(command.contractId())) {
                throw new IllegalStateException("Operation ID was already used with a different request");
            }
            if (op.installmentId() != null) return replay(company, op.installmentId());
            var request = requests.findByIdAndCompanyId(op.changeRequestId(), company).orElseThrow();
            if (request.getStatus() != ChangeRequestStatus.PENDING) throw new IllegalStateException("This proposal was decided; use a new operation ID and current capacity");
            return new Result(new BillingInstallmentApprovalResult(null, List.of(), null, request, ChangeAuthorizationMode.REQUEST_APPROVAL), true);
        }
        lockContract(company, command.contractId());
        if (command.serviceIntent() != null) command = command.withAllocations(ResolveServiceNetBilling.resolve(capacities.calculate(company, command.contractId()), command.serviceIntent()));
        var state = validate(company, command);
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            var request = changes.create(new CreateChangeRequestCommand(AuditModule.BILLING, ChangeRequestOperation.CREATE,
                    "NetBillingInstallment", null, 0, new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))), command.justification()));
            operations.remember(company, command.operationId(), command.contractId(), fingerprint, null, request.getId());
            return new Result(new BillingInstallmentApprovalResult(null, List.of(), null, request, mode), false);
        }
        var result = persist(company, command, state, null);
        operations.remember(company, command.operationId(), command.contractId(), fingerprint, result.billingInstallment().getId(), null);
        return new Result(result, false);
    }

    @Transactional
    public void apply(ChangeRequest request) {
        var command = fromProposal(request.getSnapshot().proposedData().values());
        UUID company = request.getCompanyId();
        operations.lockOperation(company, command.operationId());
        var operation = operations.findOperation(company, command.operationId()).orElseThrow();
        if (!request.getId().equals(operation.changeRequestId()) || operation.installmentId() != null
                || !operation.fingerprint().equals(fingerprint(command))) {
            throw new IllegalStateException("Net billing proposal no longer matches its immutable operation");
        }
        lockContract(company, command.contractId());
        var result = persist(company, command, validate(company, command), request.getId());
        operations.complete(company, command.operationId(), request.getId(), result.billingInstallment().getId());
    }

    private void lockContract(UUID company, UUID contract) {
        contracts.findByIdAndCompanyIdWithBillingLock(contract, company).orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
    }
    private NetBillingCapacityService.State validate(UUID company, CreateNetBillingCommand command) {
        requireCommand(command);
        var state = capacities.calculate(company, command.contractId());
        if (!state.capacityVersion().equals(command.capacityVersion())) throw new IllegalStateException("Billing capacity changed; refresh the official capacity before submitting");
        if (command.serviceIntent() != null && !ResolveServiceNetBilling.resolve(state, command.serviceIntent()).equals(command.allocations())) {
            throw new IllegalStateException("Service billing resolution no longer matches its official capacity");
        }
        var keys = new HashSet<String>();
        for (var line : command.allocations()) {
            String key = NetBillingCapacityService.key(line.contractServiceId(), line.measurementContractCompositionId());
            if (!keys.add(key)) throw new IllegalArgumentException("Each exact billing origin must appear once");
            var position = state.positions().stream().filter(p -> p.key().equals(key)).findFirst()
                    .orElseThrow(() -> new TenantResourceNotFoundException("Billing origin"));
            if (!position.eligible()) throw new IllegalStateException("This billing origin requires reconciliation or is unavailable");
            NetBillingDistribution.consumeGross(line.netAmount(), position.netAvailableAmount(), position.grossAvailableAmount());
        }
        return state;
    }
    private void requireCommand(CreateNetBillingCommand command) {
        if (command == null || command.operationId() == null || command.contractId() == null || command.dueOn() == null
                || command.capacityVersion() == null || !command.capacityVersion().matches("[a-f0-9]{64}")
                || command.allocations() == null || command.serviceIntent() == null && (command.allocations().isEmpty() || command.allocations().size() > 100)
                || command.serviceIntent() != null && (command.serviceIntent().isEmpty() || command.serviceIntent().size() > 100 || command.allocations().size() > 10000)) throw new IllegalArgumentException("Operation, contract, capacity, due date and bounded allocations/services are required");
        if (command.reference() != null && command.reference().length() > 100
                || command.description() != null && command.description().length() > 1000
                || command.justification() != null && command.justification().length() > 1000) {
            throw new IllegalArgumentException("Billing reference/description/justification exceeds its supported length");
        }
        if (command.serviceIntent() != null && command.serviceIntent().stream().anyMatch(line -> line == null || line.contractServiceId() == null || line.mode() == null)) throw new IllegalArgumentException("Service ID and mode are required");
        for (var line : command.allocations()) if (line == null || line.contractServiceId() == null || line.netAmount() == null
                || line.netAmount().signum() <= 0 || line.netAmount().stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Each service allocation requires positive canonical money");
        }
        if (command.allocations().stream().map(CreateNetBillingCommand.Line::netAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(new BigDecimal("99999999999999999.99")) > 0) throw new IllegalArgumentException("Installment exceeds supported money precision");
        // Validate domain header even when the operation will await approval.
        BillingInstallment.createApproved(UUID.randomUUID(), command.contractId(), command.reference(), command.description(), command.issuedOn(), command.dueOn());
    }
    private BillingInstallmentApprovalResult persist(UUID company, CreateNetBillingCommand command, NetBillingCapacityService.State state, UUID requestId) {
        var installment = installments.save(BillingInstallment.createApproved(company, command.contractId(), command.reference(), command.description(), command.issuedOn(), command.dueOn()));
        var lines = command.allocations().stream().map(line -> {
            var position = state.positions().stream().filter(p -> p.key().equals(NetBillingCapacityService.key(line.contractServiceId(), line.measurementContractCompositionId()))).findFirst().orElseThrow();
            BigDecimal gross = NetBillingDistribution.consumeGross(line.netAmount(), position.netAvailableAmount(), position.grossAvailableAmount());
            return BillingAllocation.createNetService(company, installment.getId(), command.contractId(), line.contractServiceId(),
                    line.measurementContractCompositionId(), line.netAmount(), gross, gross.subtract(line.netAmount())).activate();
        }).toList();
        limits.requireAvailableForActivation(company, command.contractId(), lines);
        var saved = lines.stream().map(allocations::save).sorted(java.util.Comparator.comparing(BillingAllocation::getId)).toList();
        BigDecimal net = saved.stream().map(BillingAllocation::getAllocatedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var receivable = receivables.save(Receivable.create(company, command.contractId(), installment.getId(),
                command.financialGroupId() == null ? UUID.randomUUID() : command.financialGroupId(), command.reference(), command.description(),
                command.issuedOn(), command.dueOn(), net));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operationId", command.operationId().toString());data.put("contractId", command.contractId().toString());
        if (command.serviceIntent() != null) { data.put("resolutionPolicy", "BASE_THEN_ACCEPTANCE_DATE"); data.put("serviceIntent", serviceProposal(command)); }
        data.put("netAmount", net.toPlainString());data.put("capacityVersion", command.capacityVersion());
        data.put("allocations", saved.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("allocationId", a.getId().toString());row.put("contractServiceId", a.getContractServiceId().toString());
            row.put("measurementContractCompositionId", a.getMeasurementContractCompositionId() == null ? null : a.getMeasurementContractCompositionId().toString());
            row.put("netAmount", a.getAllocatedAmount().toPlainString());row.put("grossCapacityAmount", a.getCapacityAmount().toPlainString());
            row.put("appliedContractDiscountAmount", a.getAppliedContractDiscountAmount().toPlainString());return row;
        }).toList());
        Map<String, Object> metadata = new LinkedHashMap<>();metadata.put("origin", requestId == null ? "direct" : "approved-change-request");
        if (requestId != null) metadata.put("changeRequestId", requestId.toString());
        audit.recordDirectAction(AuditModule.BILLING, AuditAction.CREATE, "BillingInstallment", installment.getId(), null, data, metadata);
        audit.recordDirectAction(AuditModule.RECEIVABLES, AuditAction.CREATE, "Receivable", receivable.getId(), null,
                Map.of("billingInstallmentId", installment.getId().toString(), "originalAmount", net.toPlainString()), metadata);
        return new BillingInstallmentApprovalResult(installment, saved, receivable, null, ChangeAuthorizationMode.DIRECT);
    }
    private Result replay(UUID company, UUID installmentId) {
        var installment = installments.findByIdAndCompanyId(installmentId, company).orElseThrow();
        var title = receivables.findByBillingInstallmentIdAndCompanyId(installmentId, company).orElseThrow();
        return new Result(new BillingInstallmentApprovalResult(installment,
                allocations.findByBillingInstallmentIdAndCompanyId(installmentId, company).stream()
                        .sorted(java.util.Comparator.comparing(BillingAllocation::getId)).toList(), title, null, ChangeAuthorizationMode.DIRECT), true);
    }
    private Map<String, Object> proposal(CreateNetBillingCommand command) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("operationId", command.operationId().toString());data.put("contractId", command.contractId().toString());
        data.put("capacityVersion", command.capacityVersion());data.put("reference", command.reference());data.put("description", command.description());
        data.put("issuedOn", command.issuedOn() == null ? null : command.issuedOn().toString());data.put("dueOn", command.dueOn().toString());
        data.put("financialGroupId", command.financialGroupId() == null ? null : command.financialGroupId().toString());
        data.put("justification", command.justification());
        if (command.serviceIntent() != null) { data.put("resolutionPolicy", "BASE_THEN_ACCEPTANCE_DATE"); data.put("serviceIntent", serviceProposal(command)); }
        data.put("allocations", command.allocations().stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>();row.put("contractServiceId", line.contractServiceId().toString());
            row.put("measurementContractCompositionId", line.measurementContractCompositionId() == null ? null : line.measurementContractCompositionId().toString());
            row.put("netAmount", line.netAmount().setScale(2).toPlainString());return row;
        }).toList());
        return data;
    }
    @SuppressWarnings("unchecked")
    private CreateNetBillingCommand fromProposal(Map<String, Object> data) {
        return new CreateNetBillingCommand(uuid(data.get("operationId")), uuid(data.get("contractId")), (String) data.get("capacityVersion"),
                (String) data.get("reference"), (String) data.get("description"), data.get("issuedOn") == null ? null : LocalDate.parse((String) data.get("issuedOn")),
                LocalDate.parse((String) data.get("dueOn")), uuid(data.get("financialGroupId")),
                ((List<Map<String, Object>>) data.get("allocations")).stream().map(row -> new CreateNetBillingCommand.Line(uuid(row.get("contractServiceId")),
                        uuid(row.get("measurementContractCompositionId")), new BigDecimal((String) row.get("netAmount")))).toList(), (String) data.get("justification"),
                data.get("serviceIntent") == null ? null : ((List<Map<String, Object>>) data.get("serviceIntent")).stream().map(row -> new CreateNetBillingCommand.ServiceLine(
                        uuid(row.get("contractServiceId")), CreateNetBillingCommand.ServiceAmountMode.valueOf((String) row.get("mode")),
                        row.get("netAmount") == null ? null : new BigDecimal((String) row.get("netAmount")))).toList());
    }
    private String fingerprint(CreateNetBillingCommand command) {
        // JSON escapes delimiters in free-text fields; Map.toString() is ambiguous across fields.
        var values = proposal(command);
        if (command.serviceIntent() != null) values.remove("allocations"); // Fingerprint user intent, not a re-resolution after the first commitment.
        return NetBillingCapacityService.hash(json.writeValueAsString(values));
    }
    private List<Map<String, Object>> serviceProposal(CreateNetBillingCommand command) {
        return command.serviceIntent().stream().map(line -> {
            Map<String, Object> row = new LinkedHashMap<>(); row.put("contractServiceId", line.contractServiceId().toString());
            row.put("mode", line.mode().name()); row.put("netAmount", line.netAmount() == null ? null : line.netAmount().toPlainString()); return row;
        }).toList();
    }
    private UUID uuid(Object value) { return value == null ? null : UUID.fromString(value.toString()); }
    public record Result(BillingInstallmentApprovalResult value, boolean replayed) { }
}
