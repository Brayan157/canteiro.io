package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementItemCreationChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementManagementService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS
                && request.getOperation() == ChangeRequestOperation.CREATE
                && request.getEntityType().equals("MeasurementItem");
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedItemCreation(request);
    }
}
