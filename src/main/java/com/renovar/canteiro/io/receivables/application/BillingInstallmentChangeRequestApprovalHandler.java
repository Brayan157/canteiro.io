package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class BillingInstallmentChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final BillingInstallmentManagementService billingInstallmentManagementService;

    @Override
    public boolean supports(ChangeRequest changeRequest) {
        return changeRequest.getModule() == AuditModule.BILLING
                && changeRequest.getOperation() == ChangeRequestOperation.CREATE
                && "BillingInstallment".equals(changeRequest.getEntityType());
    }

    @Override
    public void apply(ChangeRequest changeRequest) {
        billingInstallmentManagementService.applyApprovedCreation(changeRequest);
    }
}
