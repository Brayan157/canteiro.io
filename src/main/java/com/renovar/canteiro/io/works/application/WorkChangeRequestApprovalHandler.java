package com.renovar.canteiro.io.works.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class WorkChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final WorkManagementService workManagementService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.WORKS
                && changeRequest.getOperation() == ChangeRequestOperation.CREATE
                && "Work".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        workManagementService.applyApprovedCreation(changeRequest);
    }
}
