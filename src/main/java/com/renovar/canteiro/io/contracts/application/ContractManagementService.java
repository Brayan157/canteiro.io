package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
import com.renovar.canteiro.io.contracts.domain.ContractService;
import com.renovar.canteiro.io.contracts.domain.ContractServiceRepository;
import com.renovar.canteiro.io.contracts.domain.ContractStatus;
import com.renovar.canteiro.io.governance.application.AuditEventRecorder;
import com.renovar.canteiro.io.governance.application.ChangeRequestService;
import com.renovar.canteiro.io.governance.application.CreateChangeRequestCommand;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.AuditPayload;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.governance.domain.ChangeRequestSnapshot;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.shared.api.error.FieldViolation;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.shared.domain.CurrencyCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final WorkRepository workRepository;
    private final ContractRepository contractRepository;
    private final ContractServiceRepository contractServiceRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;
    private final ContractLifecycleService lifecycle;

    @Transactional
    public ContractChangeResult create(CreateContractCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireCurrentTenantWork(command.workId(), companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.CONTRACTS, ChangeOperation.CREATE
        );
        requireAvailableReference(companyId, command.workId(), null, null, Contract.normalizeReference(command.reference()));
        // No children or financial commitments exist yet for a new contract, including terminal empty records.
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.CONTRACTS,
                    ChangeRequestOperation.CREATE,
                    "Contract",
                    null,
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new ContractChangeResult(null, changeRequest, mode);
        }
        Contract contract = persist(companyId, command);
        auditEventRecorder.recordDirectAction(
                AuditModule.CONTRACTS, AuditAction.CREATE, "Contract", contract.getId(), null, auditData(contract),
                Map.of("origin", "direct")
        );
        return new ContractChangeResult(contract, null, mode);
    }

    @Transactional(readOnly = true)
    public Contract find(UUID contractId) {
        accessAuthorizationService.requirePermission(AccessModule.CONTRACTS, AccessAction.READ);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        return contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
    }

    @Transactional(readOnly = true)
    public Page<Contract> findAll(ContractFilter filter, Pageable pageable) {
        accessAuthorizationService.requirePermission(AccessModule.CONTRACTS, AccessAction.READ);
        return contractRepository.findByCompanyId(tenantContextHolder.requireCurrentTenant().companyId(), filter, pageable);
    }

    @Transactional
    public ContractChangeResult update(UUID contractId, UpdateContractCommand command) {
        return update(contractId, command, false, null);
    }

    @Transactional
    public ContractChangeResult updateStatus(UUID contractId, ContractStatus status, LocalDate completedOn, String justification) {
        UUID company = tenantContextHolder.requireCurrentTenant().companyId();
        var current = contractRepository.findByIdAndCompanyIdWithCancellationCheckLock(contractId, company)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        return update(contractId, statusCommand(current, status, completedOn, justification), true, completedOn);
    }

    private UpdateContractCommand statusCommand(Contract current, ContractStatus status, LocalDate completedOn, String justification) {
        return new UpdateContractCommand(current.getContractIdentifier(), current.getReference(), current.getName(), status,
                current.getStartedOn(), current.getExpectedCompletionOn(), completedOn == null ? current.getCompletedOn() : completedOn,
                justification, current.getCurrency());
    }

    private ContractChangeResult update(UUID contractId, UpdateContractCommand command, boolean statusOnly, LocalDate requestedCompletedOn) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        Contract current = contractRepository.findByIdAndCompanyIdWithCancellationCheckLock(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        boolean cancelling = command.status() == ContractStatus.CANCELLED && current.getStatus() != ContractStatus.CANCELLED;
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.CONTRACTS, cancelling ? ChangeOperation.CANCEL : ChangeOperation.UPDATE);
        if (command.currency() != null) current.getCurrency().requireSame(command.currency());
        Contract proposed = current.updateProfile(command.contractIdentifier(), command.reference(), command.name(), command.status(), command.startedOn(),
                command.expectedCompletionOn(), command.completedOn());
        lifecycle.requireContractStatus(current, proposed.getStatus());
        requireAvailableReference(companyId, current.getWorkId(), contractId, current.getReference(), proposed.getReference());
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest request = changeRequestService.create(new CreateChangeRequestCommand(AuditModule.CONTRACTS,
                    cancelling ? ChangeRequestOperation.CANCEL : ChangeRequestOperation.UPDATE, "Contract", current.getId(), 0,
                    new ChangeRequestSnapshot(new AuditPayload(auditData(current)), new AuditPayload(statusOnly ? statusProposal(command, requestedCompletedOn) : proposal(command))),
                    command.justification()));
            return new ContractChangeResult(null, request, mode);
        }
        Contract saved = contractRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.CONTRACTS, cancelling ? AuditAction.CANCEL : AuditAction.UPDATE, "Contract", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "direct"));
        return new ContractChangeResult(saved, null, mode);
    }

    @Transactional(readOnly = true)
    public java.util.List<ContractService> findServices(UUID contractId) {
        accessAuthorizationService.requirePermission(AccessModule.SERVICES, AccessAction.READ);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        contractRepository.findByIdAndCompanyId(contractId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        return contractServiceRepository.findByContractIdAndCompanyId(contractId, companyId);
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateContractCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        requireCurrentTenantWork(command.workId(), changeRequest.getCompanyId());
        requireAvailableReference(changeRequest.getCompanyId(), command.workId(), null, null,
                Contract.normalizeReference(command.reference()));
        Contract contract = persist(changeRequest.getCompanyId(), command);
        auditEventRecorder.recordDirectAction(
                AuditModule.CONTRACTS, AuditAction.CREATE, "Contract", contract.getId(), null, auditData(contract),
                Map.of("origin", "approved-change-request", "changeRequestId", changeRequest.getId().toString())
        );
    }

    @Transactional
    public void applyApprovedUpdate(ChangeRequest changeRequest) {
        Contract current = contractRepository.findByIdAndCompanyIdWithCancellationCheckLock(changeRequest.getEntityId(), changeRequest.getCompanyId())
                .orElseThrow(() -> new TenantResourceNotFoundException("Contract"));
        var values = changeRequest.getSnapshot().proposedData().values();
        UpdateContractCommand command = Boolean.TRUE.equals(values.get("statusOnly"))
                ? statusCommand(current, ContractStatus.valueOf((String) values.get("status")), date((String) values.get("completedOn")), null)
                : updateFromProposal(values);
        if (command.currency() != null) current.getCurrency().requireSame(command.currency());
        if (current.getStatus() != ContractStatus.CANCELLED && command.status() == ContractStatus.CANCELLED
                && changeRequest.getOperation() != ChangeRequestOperation.CANCEL) ContractLifecycleService.reject("CANCELLATION_REQUIRES_CANCEL_AUTHORIZATION");
        lifecycle.requireContractStatus(current, command.status());
        Contract proposed = current.updateProfile(command.contractIdentifier(), command.reference(), command.name(), command.status(),
                command.startedOn(), command.expectedCompletionOn(), command.completedOn());
        requireAvailableReference(current.getCompanyId(), current.getWorkId(), current.getId(), current.getReference(), proposed.getReference());
        Contract saved = contractRepository.save(proposed);
        auditEventRecorder.recordDirectAction(AuditModule.CONTRACTS, changeRequest.getOperation() == ChangeRequestOperation.CANCEL ? AuditAction.CANCEL : AuditAction.UPDATE, "Contract", saved.getId(),
                auditData(current), auditData(saved), Map.of("origin", "approved-change-request", "changeRequestId", changeRequest.getId().toString()));
    }

    private void requireAvailableReference(UUID companyId, UUID workId, UUID contractId,
                                           String previousReference, String reference) {
        if (reference == null || Objects.equals(previousReference, reference)) {
            return;
        }
        if (contractRepository.hasConflictingReferenceUnderWorkLock(companyId, workId, contractId, previousReference, reference)) {
            String message = "Já existe um contrato com esse número nesta obra.";
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.BUSINESS_RULE_VIOLATION, message,
                    List.of(new FieldViolation("reference", message)), "CONTRACT_REFERENCE_ALREADY_EXISTS");
        }
    }

    private void requireCurrentTenantWork(UUID workId, UUID companyId) {
        if (workId == null || workRepository.findByIdAndCompanyId(workId, companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Work");
        }
    }

    private Contract persist(UUID companyId, CreateContractCommand command) {
        return contractRepository.save(Contract.create(
                companyId, command.workId(), command.contractIdentifier(), command.pricingMode(), command.reference(), command.name(), command.status(), command.startedOn(),
                command.expectedCompletionOn(), command.completedOn(), command.currency()
        ));
    }

    private Map<String, Object> proposal(CreateContractCommand command) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("workId", command.workId().toString());
        proposal.put("contractIdentifier", command.contractIdentifier());
        proposal.put("pricingMode", (command.pricingMode() == null ? com.renovar.canteiro.io.contracts.domain.ContractPricingMode.FIXED : command.pricingMode()).name());
        proposal.put("currency", CurrencyCode.defaultIfAbsent(command.currency()).name());
        proposal.put("reference", command.reference());
        proposal.put("name", command.name());
        proposal.put("status", command.status().name());
        proposal.put("startedOn", dateValue(command.startedOn()));
        proposal.put("expectedCompletionOn", dateValue(command.expectedCompletionOn()));
        proposal.put("completedOn", dateValue(command.completedOn()));
        return proposal;
    }

    private Map<String, Object> statusProposal(UpdateContractCommand command, LocalDate requestedCompletedOn) {
        Map<String, Object> result = new HashMap<>();
        result.put("statusOnly", true); result.put("status", command.status().name());
        result.put("completedOn", dateValue(requestedCompletedOn));
        return result;
    }

    private Map<String, Object> proposal(UpdateContractCommand command) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("currency", command.currency() == null ? null : command.currency().name());
        proposal.put("contractIdentifier", command.contractIdentifier());
        proposal.put("reference", command.reference()); proposal.put("name", command.name());
        proposal.put("status", command.status().name()); proposal.put("startedOn", dateValue(command.startedOn()));
        proposal.put("expectedCompletionOn", dateValue(command.expectedCompletionOn()));
        proposal.put("completedOn", dateValue(command.completedOn()));
        return proposal;
    }

    private CreateContractCommand fromProposal(Map<String, Object> proposal) {
        return new CreateContractCommand(
                UUID.fromString((String) proposal.get("workId")),
                (String) proposal.get("contractIdentifier"),
                com.renovar.canteiro.io.contracts.domain.ContractPricingMode.valueOf((String) proposal.getOrDefault("pricingMode", "FIXED")),
                (String) proposal.get("reference"),
                (String) proposal.get("name"),
                ContractStatus.valueOf((String) proposal.get("status")),
                date((String) proposal.get("startedOn")),
                date((String) proposal.get("expectedCompletionOn")),
                date((String) proposal.get("completedOn")),
                null, CurrencyCode.valueOf((String) proposal.getOrDefault("currency", "BRL"))
        );
    }

    private UpdateContractCommand updateFromProposal(Map<String, Object> proposal) {
        return new UpdateContractCommand((String) proposal.get("contractIdentifier"), (String) proposal.get("reference"), (String) proposal.get("name"),
                ContractStatus.valueOf((String) proposal.get("status")), date((String) proposal.get("startedOn")),
                date((String) proposal.get("expectedCompletionOn")), date((String) proposal.get("completedOn")), null,
                proposal.get("currency") == null ? null : CurrencyCode.valueOf((String) proposal.get("currency")));
    }

    private Map<String, Object> auditData(Contract contract) {
        Map<String, Object> values = new HashMap<>();
        values.put("workId", contract.getWorkId().toString());
        values.put("contractIdentifier", contract.getContractIdentifier());
        values.put("pricingMode", contract.getPricingMode().name());
        values.put("currency", contract.getCurrency().name());
        values.put("name", contract.getName());
        values.put("status", contract.getStatus().name());
        return values;
    }

    private String dateValue(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
