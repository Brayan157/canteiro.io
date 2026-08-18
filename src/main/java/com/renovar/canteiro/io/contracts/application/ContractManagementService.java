package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.contracts.domain.Contract;
import com.renovar.canteiro.io.contracts.domain.ContractRepository;
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
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContractManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final WorkRepository workRepository;
    private final ContractRepository contractRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public ContractChangeResult create(CreateContractCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireCurrentTenantWork(command.workId(), companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.CONTRACTS, ChangeOperation.CREATE
        );
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

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateContractCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        requireCurrentTenantWork(command.workId(), changeRequest.getCompanyId());
        Contract contract = persist(changeRequest.getCompanyId(), command);
        auditEventRecorder.recordDirectAction(
                AuditModule.CONTRACTS, AuditAction.CREATE, "Contract", contract.getId(), null, auditData(contract),
                Map.of("origin", "approved-change-request", "changeRequestId", changeRequest.getId().toString())
        );
    }

    private void requireCurrentTenantWork(UUID workId, UUID companyId) {
        if (workId == null || workRepository.findByIdAndCompanyId(workId, companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Work");
        }
    }

    private Contract persist(UUID companyId, CreateContractCommand command) {
        return contractRepository.save(Contract.create(
                companyId, command.workId(), command.reference(), command.name(), command.status(), command.startedOn(),
                command.expectedCompletionOn(), command.completedOn()
        ));
    }

    private Map<String, Object> proposal(CreateContractCommand command) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("workId", command.workId().toString());
        proposal.put("reference", command.reference());
        proposal.put("name", command.name());
        proposal.put("status", command.status().name());
        proposal.put("startedOn", dateValue(command.startedOn()));
        proposal.put("expectedCompletionOn", dateValue(command.expectedCompletionOn()));
        proposal.put("completedOn", dateValue(command.completedOn()));
        return proposal;
    }

    private CreateContractCommand fromProposal(Map<String, Object> proposal) {
        return new CreateContractCommand(
                UUID.fromString((String) proposal.get("workId")),
                (String) proposal.get("reference"),
                (String) proposal.get("name"),
                ContractStatus.valueOf((String) proposal.get("status")),
                date((String) proposal.get("startedOn")),
                date((String) proposal.get("expectedCompletionOn")),
                date((String) proposal.get("completedOn")),
                null
        );
    }

    private Map<String, Object> auditData(Contract contract) {
        return Map.of("workId", contract.getWorkId().toString(), "name", contract.getName(),
                "status", contract.getStatus().name());
    }

    private String dateValue(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private LocalDate date(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
