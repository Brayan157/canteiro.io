package com.renovar.canteiro.io.measurements.application;

import com.renovar.canteiro.io.governance.application.ChangeRequestApprovalHandler;
import com.renovar.canteiro.io.governance.domain.AuditModule;
import com.renovar.canteiro.io.governance.domain.ChangeRequest;
import com.renovar.canteiro.io.governance.domain.ChangeRequestOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MeasurementLinkChangeRequestApprovalHandler implements ChangeRequestApprovalHandler {

    private final MeasurementLinkService service;

    @Override
    public boolean supports(ChangeRequest request) {
        return request.getModule() == AuditModule.MEASUREMENTS
                && request.getOperation() == ChangeRequestOperation.UPDATE
                && "MeasurementWorkLink".equals(request.getEntityType());
    }

    @Override
    public void apply(ChangeRequest request) {
        service.applyApprovedUpdate(request);
    }
}
