package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementCreationChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementManagementService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS
                && request.getOperation() == ChangeRequestOperation.CREATE
                && request.getEntityType().equals("Measurement");
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedMeasurementCreation(request);
    }
}
