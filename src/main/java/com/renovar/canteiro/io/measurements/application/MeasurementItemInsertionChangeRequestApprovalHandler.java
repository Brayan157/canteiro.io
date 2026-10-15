package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementItemInsertionChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementItemInsertionService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS
                && "MeasurementItemInsertion".equals(request.getEntityType());
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedInsertion(request);
    }
}
