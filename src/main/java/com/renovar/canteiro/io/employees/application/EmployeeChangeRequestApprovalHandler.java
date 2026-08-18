package com.renovar.canteiro.io.employees.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EmployeeChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {
    private final EmployeeManagementService employeeManagementService;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.EMPLOYEES
                && request.getOperation() == ChangeRequestOperation.CREATE
                && "Employee".equals(request.getEntityType());
    }

    @Override
    public void apply(ChangeRequest request) {
        employeeManagementService.applyApprovedCreation(request);
    }
}
