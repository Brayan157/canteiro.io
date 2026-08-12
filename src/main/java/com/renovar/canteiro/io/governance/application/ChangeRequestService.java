package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.access.domain.ChangeAuthorizationMode;
import com.renovar.canteiro.io.access.domain.ChangeOperation;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ChangeRequestService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ChangeRequestRepository changeRequestRepository;
    private final AuditEventRecorder auditEventRecorder;

    @Transactional
    public ChangeRequest create(CreateChangeRequestCommand command) {
        requireRequestAuthorization(command);
        var tenant = tenantContextHolder.requireCurrentTenant();
        ChangeRequest changeRequest = changeRequestRepository.save(ChangeRequest.create(
                tenant.companyId(),
                tenant.userId(),
                command.module(),
                command.operation(),
                command.entityType(),
                command.entityId(),
                command.entityVersion(),
                command.snapshot(),
                command.justification()
        ));
        auditEventRecorder.recordDirectAction(
                AuditModule.AUDIT,
                AuditAction.CREATE,
                "ChangeRequest",
                changeRequest.getId(),
                null,
                auditData(changeRequest),
                Map.of()
        );
        return changeRequest;
    }

    private void requireRequestAuthorization(CreateChangeRequestCommand command) {
        ChangeAuthorizationMode authorizationMode = accessAuthorizationService.requireChangeAuthorization(
                toAccessModule(command.module()),
                toChangeOperation(command.operation())
        );
        if (authorizationMode == ChangeAuthorizationMode.DIRECT) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A change with effective direct authority must be applied by its module use case"
            );
        }
    }

    private AccessModule toAccessModule(AuditModule module) {
        return switch (module) {
            case COMPANY -> AccessModule.COMPANY;
            case USERS -> AccessModule.USERS;
            case ROLES -> AccessModule.ROLES;
            case CUSTOMERS -> AccessModule.CUSTOMERS;
            case WORKS -> AccessModule.WORKS;
            case CONTRACTS -> AccessModule.CONTRACTS;
            case SERVICES -> AccessModule.SERVICES;
            case DISCOUNTS -> AccessModule.DISCOUNTS;
            case MEASUREMENTS -> AccessModule.MEASUREMENTS;
            case BILLING -> AccessModule.BILLING;
            case INVOICES -> AccessModule.INVOICES;
            case RECEIVABLES -> AccessModule.RECEIVABLES;
            case PAYABLES -> AccessModule.PAYABLES;
            case COSTS_EXPENSES -> AccessModule.COSTS_EXPENSES;
            case REPORTING -> AccessModule.REPORTING;
            case AUDIT -> AccessModule.AUDIT;
            case PLATFORM -> throw new IllegalArgumentException("Platform change requests are not supported");
        };
    }

    private ChangeOperation toChangeOperation(ChangeRequestOperation operation) {
        return switch (operation) {
            case CREATE -> ChangeOperation.CREATE;
            case UPDATE -> ChangeOperation.UPDATE;
            case CANCEL -> ChangeOperation.CANCEL;
        };
    }

    private Map<String, Object> auditData(ChangeRequest changeRequest) {
        Map<String, Object> changeRequestData = new HashMap<>();
        changeRequestData.put("module", changeRequest.getModule().name());
        changeRequestData.put("operation", changeRequest.getOperation().name());
        changeRequestData.put("entityType", changeRequest.getEntityType());
        changeRequestData.put("entityId", changeRequest.getEntityId() == null ? null : changeRequest.getEntityId().toString());
        changeRequestData.put("status", changeRequest.getStatus().name());
        changeRequestData.put("revision", changeRequest.getRevision());
        return changeRequestData;
    }
}
