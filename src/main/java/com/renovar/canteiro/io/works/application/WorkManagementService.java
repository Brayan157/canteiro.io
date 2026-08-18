package com.renovar.canteiro.io.works.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.application.TenantResourceNotFoundException;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.customers.domain.FinalCustomerRepository;
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
import com.renovar.canteiro.io.works.domain.Work;
import com.renovar.canteiro.io.works.domain.WorkExecutionLocationType;
import com.renovar.canteiro.io.works.domain.WorkRepository;
import com.renovar.canteiro.io.works.domain.WorkStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkManagementService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final FinalCustomerRepository finalCustomerRepository;
    private final WorkRepository workRepository;
    private final ChangeRequestService changeRequestService;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public WorkChangeResult create(CreateWorkCommand command) {
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        requireCurrentTenantCustomer(command.finalCustomerId(), companyId);
        ChangeAuthorizationMode mode = accessAuthorizationService.requireChangeAuthorization(
                AccessModule.WORKS, ChangeOperation.CREATE
        );
        if (mode == ChangeAuthorizationMode.REQUEST_APPROVAL) {
            ChangeRequest changeRequest = changeRequestService.create(new CreateChangeRequestCommand(
                    AuditModule.WORKS,
                    ChangeRequestOperation.CREATE,
                    "Work",
                    null,
                    0,
                    new ChangeRequestSnapshot(null, new AuditPayload(proposal(command))),
                    command.justification()
            ));
            return new WorkChangeResult(null, changeRequest, mode);
        }
        Work work = persist(companyId, command);
        auditEventRecorder.recordDirectAction(
                AuditModule.WORKS, AuditAction.CREATE, "Work", work.getId(), null, auditData(work), Map.of("origin", "direct")
        );
        return new WorkChangeResult(work, null, mode);
    }

    @Transactional(readOnly = true)
    public Work find(UUID workId) {
        accessAuthorizationService.requirePermission(AccessModule.WORKS, AccessAction.READ);
        UUID companyId = tenantContextHolder.requireCurrentTenant().companyId();
        return workRepository.findByIdAndCompanyId(workId, companyId)
                .orElseThrow(() -> new TenantResourceNotFoundException("Work"));
    }

    @Transactional
    public void applyApprovedCreation(ChangeRequest changeRequest) {
        CreateWorkCommand command = fromProposal(changeRequest.getSnapshot().proposedData().values());
        requireCurrentTenantCustomer(command.finalCustomerId(), changeRequest.getCompanyId());
        Work work = persist(changeRequest.getCompanyId(), command);
        auditEventRecorder.recordDirectAction(
                AuditModule.WORKS, AuditAction.CREATE, "Work", work.getId(), null, auditData(work),
                Map.of("origin", "approved-change-request", "changeRequestId", changeRequest.getId().toString())
        );
    }

    private void requireCurrentTenantCustomer(UUID customerId, UUID companyId) {
        if (customerId == null || finalCustomerRepository.findByIdAndCompanyId(customerId, companyId).isEmpty()) {
            throw new TenantResourceNotFoundException("Final customer");
        }
    }

    private Work persist(UUID companyId, CreateWorkCommand command) {
        return workRepository.save(Work.create(
                companyId, command.finalCustomerId(), command.name(), command.reference(), command.executionLocationType(),
                command.executionAddress(), command.status(), command.startedOn(), command.expectedCompletionOn(), command.completedOn()
        ));
    }

    private Map<String, Object> proposal(CreateWorkCommand command) {
        Map<String, Object> proposal = new HashMap<>();
        proposal.put("finalCustomerId", command.finalCustomerId().toString());
        proposal.put("name", command.name());
        proposal.put("reference", command.reference());
        proposal.put("executionLocationType", command.executionLocationType().name());
        proposal.put("executionAddress", command.executionAddress());
        proposal.put("status", command.status().name());
        proposal.put("startedOn", dateValue(command.startedOn()));
        proposal.put("expectedCompletionOn", dateValue(command.expectedCompletionOn()));
        proposal.put("completedOn", dateValue(command.completedOn()));
        return proposal;
    }

    private CreateWorkCommand fromProposal(Map<String, Object> proposal) {
        return new CreateWorkCommand(
                UUID.fromString((String) proposal.get("finalCustomerId")),
                (String) proposal.get("name"),
                (String) proposal.get("reference"),
                WorkExecutionLocationType.valueOf((String) proposal.get("executionLocationType")),
                (String) proposal.get("executionAddress"),
                WorkStatus.valueOf((String) proposal.get("status")),
                date((String) proposal.get("startedOn")),
                date((String) proposal.get("expectedCompletionOn")),
                date((String) proposal.get("completedOn")),
                null
        );
    }

    private Map<String, Object> auditData(Work work) {
        return Map.of("finalCustomerId", work.getFinalCustomerId().toString(), "name", work.getName(),
                "status", work.getStatus().name(), "executionLocationType", work.getExecutionLocationType().name());
    }

    private String dateValue(LocalDate value) { return value == null ? null : value.toString(); }
    private LocalDate date(String value) { return value == null ? null : LocalDate.parse(value); }
}
