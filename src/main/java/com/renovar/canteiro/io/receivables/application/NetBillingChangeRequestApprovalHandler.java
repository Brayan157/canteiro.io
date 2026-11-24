package com.renovar.canteiro.io.receivables.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NetBillingChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final CreateNetBillingService billing;
    @Override public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.BILLING && request.getOperation() == ChangeRequestOperation.CREATE
                && "NetBillingInstallment".equals(request.getEntityType());
    }
    @Override public void apply(ChangeRequest request) { billing.apply(request); }
}
