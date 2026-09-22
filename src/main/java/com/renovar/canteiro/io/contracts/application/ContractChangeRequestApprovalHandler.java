package com.renovar.canteiro.io.contracts.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ContractChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final ContractManagementService contractManagementService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.CONTRACTS
                && (changeRequest.getOperation() == ChangeRequestOperation.CREATE || changeRequest.getOperation() == ChangeRequestOperation.UPDATE)
                && "Contract".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        if (changeRequest.getOperation() == ChangeRequestOperation.CREATE) {
            contractManagementService.applyApprovedCreation(changeRequest);
        } else {
            contractManagementService.applyApprovedUpdate(changeRequest);
        }
    }
}
