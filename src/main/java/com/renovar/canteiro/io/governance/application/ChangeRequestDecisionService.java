package com.renovar.canteiro.io.governance.application;

import com.renovar.canteiro.io.access.application.AccessAuthorizationService;
import com.renovar.canteiro.io.access.domain.AccessAction;
import com.renovar.canteiro.io.access.domain.AccessModule;
import com.renovar.canteiro.io.governance.domain.AuditAction;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestRepository;
import com.renovar.canteiro.io.governance.domain.ChangeRequestStatus;
import com.renovar.canteiro.io.shared.api.error.ApiException;
import com.renovar.canteiro.io.shared.api.error.ErrorCode;
import com.renovar.canteiro.io.tenancy.application.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChangeRequestDecisionService {

    private final TenantContextHolder tenantContextHolder;
    private final AccessAuthorizationService accessAuthorizationService;
    private final ChangeRequestRepository changeRequestRepository;
    private final AuditEventRecorder auditEventRecorder;
    private final Clock clock;

    @Transactional
    public ChangeRequest approve(ApproveChangeRequestCommand command) {
        ChangeRequest changeRequest = requirePendingRequest(command.changeRequestId());
        requireApprovalPermission(changeRequest.getModule(), AccessAction.APPROVE);
        var tenant = tenantContextHolder.requireCurrentTenant();
        preventSelfApproval(changeRequest, tenant.userId());
        Map<String, Object> beforeData = decisionAuditData(changeRequest);
        changeRequest.approve(tenant.userId(), clock.instant(), command.decisionReason());
        ChangeRequest decidedChangeRequest = changeRequestRepository.save(changeRequest);
        recordDecision(AuditAction.APPROVE, decidedChangeRequest, beforeData);
        return decidedChangeRequest;
    }

    @Transactional
    public ChangeRequest reject(RejectChangeRequestCommand command) {
        requireRejectionReason(command.decisionReason());
        ChangeRequest changeRequest = requirePendingRequest(command.changeRequestId());
        requireApprovalPermission(changeRequest.getModule(), AccessAction.REJECT);
        var tenant = tenantContextHolder.requireCurrentTenant();
        preventSelfApproval(changeRequest, tenant.userId());
        Map<String, Object> beforeData = decisionAuditData(changeRequest);
        changeRequest.reject(tenant.userId(), clock.instant(), command.decisionReason());
        ChangeRequest decidedChangeRequest = changeRequestRepository.save(changeRequest);
        recordDecision(AuditAction.REJECT, decidedChangeRequest, beforeData);
        return decidedChangeRequest;
    }

    private ChangeRequest requirePendingRequest(UUID changeRequestId) {
        var tenant = tenantContextHolder.requireCurrentTenant();
        ChangeRequest changeRequest = changeRequestRepository.findWithLockByIdAndCompanyId(changeRequestId, tenant.companyId())
                .orElseThrow(ChangeRequestNotFoundException::new);
        if (changeRequest.getStatus() != ChangeRequestStatus.PENDING) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Only pending change requests can be decided"
            );
        }
        return changeRequest;
    }

    private void requireApprovalPermission(AuditModule module, AccessAction action) {
        accessAuthorizationService.requirePermission(toAccessModule(module), action);
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
            case PLATFORM -> throw new AccessDeniedException("Platform change requests cannot be decided by a company user");
        };
    }

    private void preventSelfApproval(ChangeRequest changeRequest, UUID approverUserId) {
        if (changeRequest.getRequesterUserId().equals(approverUserId)) {
            throw new AccessDeniedException("A requester cannot decide their own change request");
        }
    }

    private void requireRejectionReason(String decisionReason) {
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.BUSINESS_RULE_VIOLATION,
                    "A reason is required when rejecting a change request"
            );
        }
    }

    private void recordDecision(AuditAction action, ChangeRequest changeRequest, Map<String, Object> beforeData) {
        auditEventRecorder.recordDirectAction(
                AuditModule.AUDIT,
                action,
                "ChangeRequest",
                changeRequest.getId(),
                beforeData,
                decisionAuditData(changeRequest),
                Map.of("changeRequestModule", changeRequest.getModule().name())
        );
    }

    private Map<String, Object> decisionAuditData(ChangeRequest changeRequest) {
        Map<String, Object> data = new HashMap<>();
        data.put("status", changeRequest.getStatus().name());
        data.put("decidedByUserId", changeRequest.getDecidedByUserId() == null
                ? null
                : changeRequest.getDecidedByUserId().toString());
        data.put("decisionReason", changeRequest.getDecisionReason());
        data.put("decidedAt", changeRequest.getDecidedAt() == null ? null : changeRequest.getDecidedAt().toString());
        return data;
    }
}
