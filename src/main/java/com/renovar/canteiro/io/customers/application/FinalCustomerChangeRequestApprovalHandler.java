package com.renovar.canteiro.io.customers.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class FinalCustomerChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final FinalCustomerManagementService finalCustomerManagementService;
    public boolean supports(ChangeRequest request) { return request.getModule() == AuditModule.CUSTOMERS && request.getOperation() == ChangeRequestOperation.CREATE && request.getEntityType().equals("FinalCustomer"); }
    public void apply(ChangeRequest request) { finalCustomerManagementService.applyApprovedCreation(request); }
}
